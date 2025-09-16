package bas.app.shift.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.databinding.ItemDisciplineBinding
import bas.app.shift.models.NamedEntity

class DisciplinesAdapter(
    private val onDisciplineSelected: (NamedEntity) -> Unit
) : RecyclerView.Adapter<DisciplinesAdapter.DisciplineViewHolder>() {

    private var disciplines: List<NamedEntity> = emptyList()
    private var selectedDiscipline: NamedEntity? = null

    fun updateDisciplines(newDisciplines: List<NamedEntity>) {
        disciplines = newDisciplines
        android.util.Log.d("DisciplinesAdapter", "Updated disciplines: ${disciplines.size}")
        notifyDataSetChanged()
    }

    fun getSelectedDiscipline(): NamedEntity? = selectedDiscipline

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisciplineViewHolder {
        val binding = ItemDisciplineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DisciplineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DisciplineViewHolder, position: Int) {
        android.util.Log.d("DisciplinesAdapter", "Binding position $position: ${disciplines[position].name}")
        holder.bind(disciplines[position])
    }

    override fun getItemCount(): Int = disciplines.size

    inner class DisciplineViewHolder(
        private val binding: ItemDisciplineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(discipline: NamedEntity) {
            android.util.Log.d("DisciplinesAdapter", "Binding discipline: ${discipline.name}")
            binding.apply {
                tvDisciplineName.text = discipline.name
                android.util.Log.d("DisciplinesAdapter", "Set text: ${tvDisciplineName.text}")
                ivCheck.visibility = if (selectedDiscipline?.id == discipline.id) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                root.setOnClickListener {
                    android.util.Log.d("DisciplinesAdapter", "Clicked on: ${discipline.name}")
                    selectedDiscipline = discipline
                    notifyDataSetChanged()
                    onDisciplineSelected(discipline)
                }
            }
        }
    }
}
