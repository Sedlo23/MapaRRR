package com.example.mapa


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaceAdapter(private val placeList: List<PlaceInfo>, private val onReshareClickListener: (PlaceInfo) -> Unit) :
    RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder>() {

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeImageView: ImageView = itemView.findViewById(R.id.placeImageView)
        val mapScreenshotImageView: ImageView = itemView.findViewById(R.id.mapScreenshotImageView)
        val placeTextView: TextView = itemView.findViewById(R.id.placeTextView)
        val reshareButton: Button = itemView.findViewById(R.id.reshareButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = placeList[position]
        holder.placeImageView.setImageBitmap(place.photo)
        holder.mapScreenshotImageView.setImageBitmap(place.mapScreenshot)
        holder.placeTextView.text = place.place.text
        holder.reshareButton.setOnClickListener {
            onReshareClickListener(place)
        }
    }

    override fun getItemCount(): Int {
        return placeList.size
    }
}