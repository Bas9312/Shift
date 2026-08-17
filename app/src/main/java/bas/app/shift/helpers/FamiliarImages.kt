package bas.app.shift.helpers

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import java.util.Calendar

/**
 * Сборка URL картинок фамильяра и их предзагрузка.
 *
 * Раньше картинка искалась в drawable через resources.getIdentifier по склеенному имени
 * ("familiar_fox" + "2" + "_night"), и опечатка молча давала resId=0. Теперь набор картинок
 * приходит из каталога явным списком вариантов.
 */
object FamiliarImages {

    const val VARIANT_NIGHT = "night"

    /** Ночь с 2:00 до 10:00 — как было в старом FamiliarData.isNightTime. */
    fun isNightTime(hour: Int): Boolean = hour in 2..9

    /**
     * Какой вариант показывать сейчас. Днём картинка меняется по часам, ночью она одна.
     */
    fun variantForHour(hour: Int): String =
        if (isNightTime(hour)) VARIANT_NIGHT else "day${(hour % 3) + 1}"

    fun currentVariant(): String =
        variantForHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    /**
     * URL картинки, или null если каталога нет, фамильяр неизвестен или у него нет артов.
     * image_version в query — это сброс кеша: поменяли арт на сервере, подняли версию,
     * у всех клиентов URL стал другим и Coil скачал новое.
     */
    fun urlFor(id: String?, variant: String): String? {
        val catalog = FamiliarCatalog.snapshot() ?: return null
        val familiar = FamiliarCatalog.get(id) ?: return null
        if (familiar.variants.isEmpty()) return null
        val chosen = if (variant in familiar.variants) variant else familiar.variants.first()
        return "${catalog.baseUrl}${familiar.id}/$chosen.webp?v=${familiar.imageVersion}"
    }

    fun urlForNow(id: String?): String? = urlFor(id, currentVariant())

    /**
     * Тянет все картинки фамильяра в кеш заранее. Зовём при логине и при смене фамильяра,
     * чтобы в поле без сети экран фамильяра всё равно рисовался.
     */
    fun prefetch(context: Context, id: String?) {
        val familiar = FamiliarCatalog.get(id) ?: return
        val loader = SingletonImageLoader.get(context)
        familiar.variants.forEach { variant ->
            val url = urlFor(familiar.id, variant) ?: return@forEach
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .build()
            )
        }
        LogHelper.d("FamiliarImages: предзагрузка ${familiar.id}, вариантов=${familiar.variants.size}")
    }
}
