package bas.app.shift.ui.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.models.MessageAttachment
import bas.app.shift.ui.ImageViewerActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class AttachmentsAdapter(
    private val onAttachmentClick: (MessageAttachment) -> Unit = {}
) : RecyclerView.Adapter<AttachmentsAdapter.AttachmentViewHolder>() {

    private var attachments: List<MessageAttachment> = emptyList()

    fun updateAttachments(newAttachments: List<MessageAttachment>) {
        attachments = newAttachments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attachment_image, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        holder.bind(attachments[position])
    }

    override fun getItemCount(): Int = attachments.size

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAttachment: ImageView = itemView.findViewById(R.id.ivAttachment)
        private val tvAttachmentName: TextView = itemView.findViewById(R.id.tvAttachmentName)

        fun bind(attachment: MessageAttachment) {
            // Показываем имя файла
            tvAttachmentName.text = attachment.originalName

            // Загружаем изображение
            if (attachment.fileType == "image") {
                val imageSource = if (attachment.filePath.startsWith("content://") || attachment.filePath.startsWith("file://") || attachment.filePath.startsWith("http://") || attachment.filePath.startsWith("https://")) {
                    // Локальный файл или уже полный URL
                    if (attachment.filePath.startsWith("content://") || attachment.filePath.startsWith("file://")) {
                        android.net.Uri.parse(attachment.filePath)
                    } else {
                        attachment.filePath
                    }
                } else {
                    // Относительный путь с сервера - добавляем базовый URL
                    "https://shift96.ru/messages_api/${attachment.filePath}"
                }
                
                android.util.Log.d("AttachmentsAdapter", "Loading image from: $imageSource")
                Glide.with(itemView.context)
                    .load(imageSource)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivAttachment)
            } else {
                // Для не-изображений показываем заглушку
                ivAttachment.setImageResource(R.drawable.ic_image_placeholder)
            }

            // Обработчик клика
            itemView.setOnClickListener {
                if (attachment.fileType == "image") {
                    // Для изображений открываем в полном размере
                    val context = itemView.context
                    val intent = Intent(context, ImageViewerActivity::class.java).apply {
                        putExtra("image_url", attachment.filePath)
                        putExtra("file_name", attachment.originalName)
                    }
                    context.startActivity(intent)
                } else {
                    // Для других файлов используем стандартный обработчик
                    onAttachmentClick(attachment)
                }
            }
        }
    }
}
