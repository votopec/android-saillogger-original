package com.ibsailing.saillogger

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.ibsailing.saillogger.databinding.QrShareLayoutBinding
import com.ibsailing.saillogger.databinding.ShareItemLayoutBinding
import java.io.File
import java.time.ZoneId

class ShareAdapter(private val items: List<File>, private val maxQrItems:Int=5) :
    RecyclerView.Adapter<ShareAdapter.ViewHolder>() {

    private lateinit var qrBinding:QrShareLayoutBinding

    private val qrClickListener: View.OnClickListener=View.OnClickListener {
        qrBinding= QrShareLayoutBinding.inflate(LayoutInflater.from(it.context),it.rootView as ViewGroup,false)
        val importedEventsList=getEventsList(it)
        if(importedEventsList.isNotEmpty()){
            var qrCodesCount=0
            var i= maxQrItems
            val qrStringBuilder=StringBuilder()
            val textStringBuilder=StringBuilder()
            for(event in importedEventsList){
                qrStringBuilder.append("${event.toCSV()}\n")
               textStringBuilder.append("${event.toShortString()}\n")
                if(i <= 1 || event==importedEventsList.last()){
              val textView=TextView(it.context)
              textView.text= textStringBuilder.toString()
              qrBinding.qrLinearLayout.addView(textView)
              val view=ImageView(it.context)
              view.setImageBitmap(getQRBmp(qrStringBuilder.toString(), it.context.resources.displayMetrics.widthPixels))
              qrBinding.qrLinearLayout.addView(view)
                    qrCodesCount++
              qrStringBuilder.clear()
                    textStringBuilder.clear()
              i= maxQrItems+1
                }
                i--
            }


AlertDialog.Builder(it.context).setTitle("Events QR")
    .setMessage(it.context.resources.getQuantityString(R.plurals.scan_qr_title,qrCodesCount,qrCodesCount))
    .setView(qrBinding.root)
    .setPositiveButton("Close"){ _, _ ->  }
    .show()
        }
    }


    private val copyClickListener:View.OnClickListener=View.OnClickListener {
        val clipboard: ClipboardManager = it.context.applicationContext.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager
        val importedEventsList= getEventsList(it)
        if(importedEventsList.isEmpty()){return@OnClickListener}
        val qrStringBuilder=StringBuilder()
        for(event in importedEventsList) {
            qrStringBuilder.append(event.toCSV())
            qrStringBuilder.append("\n")
        }
        val clip = ClipData.newPlainText("label", qrStringBuilder.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(it.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private val shareClickListener:View.OnClickListener=View.OnClickListener {
        val file=items[it.tag as Int]
        val intent = Intent(Intent.ACTION_SEND)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val uri = FileProvider.getUriForFile(it.context, BuildConfig.APPLICATION_ID + ".fileprovider", file)
        intent.setDataAndType(uri, "text/csv")
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        it.context.startActivity(Intent.createChooser(intent, "Share File ${file.name}"))
    }

    lateinit var binding: ShareItemLayoutBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        binding=ShareItemLayoutBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.qrButton.tag=position
        holder.copyButton.tag=position
        holder.sendButton.tag=position
        holder.fileNameTextView.text = item.name.substringBeforeLast("_")
        holder.sizeTextView.text = formatFileSize(item.length())
        holder.typeTextView.text = if(item.nameWithoutExtension.endsWith("Events",ignoreCase = true)){ "Events: ${getEventsList(holder.copyButton).size}"} else "Log"
        holder.qrButton.visibility = if(item.nameWithoutExtension.endsWith("Events",ignoreCase = true)) View.VISIBLE else View.GONE
        holder.copyButton.visibility = if(item.nameWithoutExtension.endsWith("Events",ignoreCase = true)) View.VISIBLE else View.GONE

        holder.copyButton.setOnClickListener(copyClickListener)
        holder.qrButton.setOnClickListener(qrClickListener)
        holder.sendButton.setOnClickListener(shareClickListener)

    }


    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = binding.fileNameTextView
        val sizeTextView: TextView = binding.sizeTextView
        val typeTextView: TextView = binding.typeTextView
        val qrButton = binding.showQrButton
        val sendButton= binding.sendFileButton
        val copyButton= binding.eventsCopyButton
    }

    fun formatFileSize(size: Long): String {
        val kilobytes = size / 1024.0
        if (kilobytes < 1024.0) {
            return "%.1f KB".format(kilobytes)
        }
        val megabytes = kilobytes / 1024.0
        return "%.1f MB".format(megabytes)
    }


    private fun getQRBmp(str:String, size:Int):Bitmap{
        val writer = QRCodeWriter()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        try {
            val bitMatrix: BitMatrix = writer.encode(str, BarcodeFormat.QR_CODE, size, size)
            val width: Int = bitMatrix.width
            val height: Int = bitMatrix.height

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return bmp
    }

    private fun getEventsList(clickedView:View):ArrayList<LogEvent>{
        val file = items[clickedView.tag as Int]
        if(file.nameWithoutExtension.endsWith("Events")) {
            val text = file.readText()
            val arrayList=Gson().fromJson(text, Array<LogEvent>::class.java).toCollection(ArrayList())
            arrayList.sortBy { it.startTimeStamp }
            return arrayList
        }
        return ArrayList()
    }

}