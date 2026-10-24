package com.saha.batchuninstaller.ui.main.adapters

import android.content.Context
import android.content.res.Resources
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.R
import com.saha.batchuninstaller.debloat.DebloatRisk
import com.saha.batchuninstaller.debloat.UadPackageRecord
import com.saha.batchuninstaller.debloat.UadRiskMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppInfoAdapter(private val mContext: Context, private val mAppsList: List<AppInfo>, private val onItemClick: (Int) -> Unit) : RecyclerView.Adapter<AppInfoAdapter.ItemViewHolder>() {
    private var debloatEntries: Map<String, UadPackageRecord> = emptyMap()

    fun updateDebloatEntries(entries: Map<String, UadPackageRecord>) {
        debloatEntries = entries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
        val holder = ItemViewHolder(view)
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onItemClick(position)
        }
        return holder
    }

    @Throws(Resources.NotFoundException::class)
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val app = mAppsList[position]
        holder.mLayoutItem.setBackgroundColor(mContext.resources.getColor(app.color))
        if (app.icon != null) holder.mImgIcon.setImageBitmap(app.icon) else holder.mImgIcon.setImageResource(R.mipmap.ic_launcher_round)
        holder.mTvAppName.text = app.appName
        holder.mTvAppSize.text = Formatter.formatShortFileSize(mContext, app.fileSize)
        holder.mTvPackageName.text = app.packageName ?: mContext.getString(R.string.package_unknown)
        holder.mTvAppDate.text = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(app.firstInstallTime))

        holder.mTvDebloatInfo.visibility = if (app.color == R.color.backgroundSelected) View.VISIBLE else View.GONE
        val entry = app.packageName?.let { debloatEntries[it] }
        if (entry == null) {
            holder.mTvDebloatInfo.text = mContext.getString(R.string.debloat_no_match)
            holder.mTvDebloatInfo.setBackgroundResource(R.drawable.debloat_info_background)
        } else {
            val risk = UadRiskMapper.fromRemoval(entry.removal)
            val explanation = entry.description ?: mContext.getString(R.string.debloat_no_description)
            val source = listOfNotNull(entry.list, entry.removal).joinToString(" · ")
            val notes = entry.labels.orEmpty().takeIf { it.isNotEmpty() }?.joinToString(", ")
            holder.mTvDebloatInfo.text = buildString {
                append(risk.displayName)
                if (source.isNotBlank()) append("  ·  ").append(source)
                append("\n").append(explanation)
                if (notes != null) append("\nLabels: ").append(notes)
                if (!entry.dependencies.isNullOrEmpty()) append("\nDependencies: ").append(entry.dependencies.joinToString(", "))
                if (!entry.neededBy.isNullOrEmpty()) append("\nNeeded by: ").append(entry.neededBy.joinToString(", "))
            }
            val background = when (risk) {
                DebloatRisk.RECOMMENDED -> R.drawable.debloat_info_recommended
                DebloatRisk.CAUTION -> R.drawable.debloat_info_caution
                DebloatRisk.HIGH_RISK -> R.drawable.debloat_info_high_risk
                DebloatRisk.UNKNOWN -> R.drawable.debloat_info_background
            }
            holder.mTvDebloatInfo.setBackgroundResource(background)
        }
    }

    override fun getItemCount(): Int = mAppsList.size
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean = false
    override fun onItemDismiss(position: Int, direction: Int) = notifyDataSetChanged()

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mLayoutItem: RelativeLayout = itemView.findViewById(R.id.layout_appitem)
        val mTvAppName: TextView = itemView.findViewById(R.id.tv_appname)
        val mTvAppSize: TextView = itemView.findViewById(R.id.tv_appsize)
        val mTvAppDate: TextView = itemView.findViewById(R.id.tv_date)
        val mTvPackageName: TextView = itemView.findViewById(R.id.tv_package_name)
        val mTvDebloatInfo: TextView = itemView.findViewById(R.id.tv_debloat_info)
        val mImgIcon: ImageView = itemView.findViewById(R.id.img_icon)
        override fun onItemSelected(actionstate: Int) = Unit
        override fun onItemClear() = Unit
    }
}
