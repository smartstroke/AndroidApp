package com.example.smartstrokeapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast


class PastSessionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_past_session)

        supportActionBar?.hide()
        val backButton: Button = findViewById(R.id.Backbutton)
        backButton.setOnClickListener {
            onBackPressed()
        }
        viewManager = LinearLayoutManager(this)
        viewAdapter = FileListAdapter(getFileList())

        recyclerView = findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }

    private fun getFileList(): MutableList<File> {
        val directory = getExternalFilesDir(null)
        return directory?.listFiles()?.sortedWith(compareBy { it.lastModified() })?.toMutableList() ?: mutableListOf()
    }


    private inner class FileListAdapter(private val fileList: MutableList<File>) :
        RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val fileNameTextView: TextView = itemView.findViewById(R.id.text_file_name)
}

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_file, parent, false)
    return FileViewHolder(view)
}


@SuppressLint("SetTextI18n", "NotifyDataSetChanged")
override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
    val file = fileList[position]
    val fileName = file.name

    val regex = """Session_results_from_(\d{4}_\d{2}_\d{2}_\d{2}_\d{2})\.txt""".toRegex()
    val matchResult = regex.find(fileName)

    if (matchResult != null) {
        val dateTimeString = matchResult.groupValues.getOrElse(1) { "" }
        if (dateTimeString.isNotBlank()) {
            val formattedFileName = "Post Processing Report ${formatDateTimeString(dateTimeString)}"
            holder.fileNameTextView.text = "${position + 1}. $formattedFileName"
        } else {
            holder.fileNameTextView.text = "${position + 1}. $fileName"
        }
    } else {
        holder.fileNameTextView.text = "${position + 1}. $fileName"
    }

    holder.itemView.setOnClickListener {
        openFile(file)
    }


    holder.itemView.setOnLongClickListener {
        // Display an AlertDialog with two options: Delete and Rename
        AlertDialog.Builder(this@PastSessionActivity)
            .setTitle("Choose Action")
            .setMessage("Do you want to delete or rename this file?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete file
                if (file.delete()) {
                    fileList.removeAt(position) // remove file from list
                    notifyDataSetChanged() // Refresh the RecyclerView
                    Toast.makeText(this@PastSessionActivity, "File deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PastSessionActivity, "Unable to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Rename") { _, _ ->
                // Rename file
                val builder = AlertDialog.Builder(this@PastSessionActivity)
                val inflater = layoutInflater
                builder.setTitle("Rename File")
                val dialogLayout = inflater.inflate(R.layout.rename_file_dialog, null)
                val editText = dialogLayout.findViewById<EditText>(R.id.editText)
                builder.setView(dialogLayout)
                builder.setPositiveButton("OK") { _, _ ->
                    val newName = editText.text.toString()
                    // Ensure .txt extension is kept
                    val formattedName = if (!newName.endsWith(".txt")) "$newName.txt" else newName
                    if (file.renameTo(File(file.parentFile, formattedName))) {
                        fileList[position] = File(file.parentFile, formattedName) // rename file in list
                        notifyDataSetChanged() // Refresh the RecyclerView
                        Toast.makeText(this@PastSessionActivity, "File renamed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PastSessionActivity, "Unable to rename file", Toast.LENGTH_SHORT).show()
                    }
                }
                builder.setNegativeButton("Cancel") { _, _ -> }
                builder.show()
            }
            .show()

        true // Consumes the long click event
    }

}


private fun formatDateTimeString(dateTimeString: String): String {
    val year = dateTimeString.substring(0, 4)
    val month = dateTimeString.substring(5, 7)
    val day = dateTimeString.substring(8, 10)
    val hour = dateTimeString.substring(11, 13)
    val minute = dateTimeString.substring(14, 16)

    return "$year/$month/$day/$hour:$minute"
}





        override fun getItemCount() = fileList.size
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(intent)
    }
}
