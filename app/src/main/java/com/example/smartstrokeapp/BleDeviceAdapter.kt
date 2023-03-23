package com.example.smartstrokeapp

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

interface BleDeviceClickListener {
    fun onBleDeviceClick(deviceWrapper: BluetoothDeviceWrapper)
}

class BleDeviceAdapter(
    private val mList: MutableList<BluetoothDeviceWrapper>,
    private val bleDeviceClickListener: BleDeviceClickListener
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_card, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deviceWrapper = mList[position]
        holder.textView.text = deviceWrapper.device.name ?: "Unknown Device"
        holder.statusTextView.text = if(deviceWrapper.connected) "Connected" else "Not Connected"
        holder.itemView.setOnClickListener { bleDeviceClickListener.onBleDeviceClick(deviceWrapper) }
    }

    override fun getItemCount(): Int {
        return mList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.textView)
        val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
    }
}
