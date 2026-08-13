package com.ibsailing.saillogger

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ibsailing.saillogger.databinding.FragmentShareBinding
import java.io.File



class ShareFragment : Fragment() {

   lateinit var binding:FragmentShareBinding
   lateinit var viewModel: ViewModelMain

        lateinit var adapter: ShareAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ViewModelMain::class.java]
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding=FragmentShareBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.navigateBackButton.setOnClickListener {
            findNavController().navigateUp()
        }

        val fileList = requireActivity().applicationContext.getExternalFilesDir(null)
            ?.listFiles()
            ?.filter { isShareableFile(it) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
        adapter =  ShareAdapter(fileList,viewModel.eventsPerQr)

        binding.shareEmptyTextView.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
        binding.shareRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.shareRecyclerView.adapter = adapter
    }


    companion object {
        const val TAG="ShareFragment"

        fun isShareableFile(file: File): Boolean {
            if (file.isDirectory || file.name.endsWith(DurableLogWriter.IN_PROGRESS_SUFFIX)) {
                return false
            }
            return file.extension.equals("csv", ignoreCase = true) ||
                file.nameWithoutExtension.endsWith("events", ignoreCase = true)
        }
    }



}
