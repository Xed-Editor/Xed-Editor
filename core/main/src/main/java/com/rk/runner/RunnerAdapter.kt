package com.rk.runner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R

class RunnerAdapter(
    private val runners: List<RunnerImpl>,
    private val dialog: AlertDialog,
    private val onRunnerSelected: (RunnerImpl) -> Unit,
) : RecyclerView.Adapter<RunnerAdapter.RunnerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunnerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_runner, parent, false)
        return RunnerViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunnerViewHolder, position: Int) {
        val runner = runners[position]
        holder.bind(runner)
    }

    override fun getItemCount(): Int = runners.size

    inner class RunnerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.runner_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.runner_description)
        private val iconView: ImageView = itemView.findViewById(R.id.runner_icon)

        fun bind(runner: RunnerImpl) {
            titleView.text = runner.getName()
            descriptionView.text = runner.getDescription()
            iconView.setImageDrawable(
                runner.getIcon(itemView.context)
                    ?: ContextCompat.getDrawable(itemView.context, R.drawable.android)
            )

            itemView.setOnClickListener {
                dialog.dismiss()
                onRunnerSelected(runner)
            }
        }
    }
}
