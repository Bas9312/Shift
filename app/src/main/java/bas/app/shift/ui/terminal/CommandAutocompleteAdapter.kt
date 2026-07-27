package bas.app.shift.ui.terminal

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import bas.app.shift.R
import bas.app.shift.helpers.TerminalCommandManager
import bas.app.shift.models.TerminalCommand

class CommandAutocompleteAdapter(
    private val context: Context,
    private val availableModules: List<Int> = emptyList()
) : BaseAdapter(), Filterable {
    
    private val commands = TerminalCommandManager.getAvailableCommands(availableModules)
    private var filteredCommands = commands
    
    override fun getCount(): Int = filteredCommands.size
    
    override fun getItem(position: Int): TerminalCommand? {
        return if (position >= 0 && position < filteredCommands.size) {
            filteredCommands[position]
        } else null
    }
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dropdown_command_with_desc, parent, false)
        
        // Проверяем границы массива
        if (position < 0 || position >= filteredCommands.size) {
            return view
        }
        
        val command = filteredCommands[position]
        
        val commandName = view.findViewById<TextView>(R.id.commandName)
        val commandDescription = view.findViewById<TextView>(R.id.commandDescription)
        
        commandName.text = command.fullCommand
        commandDescription.text = command.description
        
        return view
    }
    
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    filteredCommands = commands
                } else {
                    filteredCommands = commands.filter { command ->
                        command.name.contains(constraint.toString(), ignoreCase = true) ||
                        command.fullCommand.contains(constraint.toString(), ignoreCase = true) ||
                        command.description.contains(constraint.toString(), ignoreCase = true)
                    }
                }
                results.values = filteredCommands
                results.count = filteredCommands.size
                return results
            }
            
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null) {
                    @Suppress("UNCHECKED_CAST")
                    val newFilteredCommands = results.values as List<TerminalCommand>
                    // Убеждаемся, что новый список не null и не пустой
                    filteredCommands = newFilteredCommands ?: emptyList()
                    notifyDataSetChanged()
                }
            }
        }
    }
    
    fun getCommandAt(position: Int): TerminalCommand? {
        return if (position in 0 until filteredCommands.size) {
            filteredCommands[position]
        } else null
    }
}
