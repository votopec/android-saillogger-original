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

        val path = requireActivity().applicationContext.getExternalFilesDir(null)
        val files=path?.listFiles()
        val fileList=files?.toMutableList()
        fileList?.let{ list ->
            list.removeIf { it.isDirectory || it.extension!="csv" && !it.nameWithoutExtension.endsWith("events",true)}
            list.sortByDescending { it.name }
            adapter =  ShareAdapter(list,viewModel.eventsPerQr)
        }

        binding.shareRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.shareRecyclerView.adapter = adapter
    }


    companion object {
        const val TAG="ShareFragment"
    }



}