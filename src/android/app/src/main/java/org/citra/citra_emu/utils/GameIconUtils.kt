// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.utils

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.Options
import coil.transform.RoundedCornersTransformation
import java.nio.IntBuffer
import org.citra.citra_emu.R
import org.citra.citra_emu.model.Game

class GameIconFetcher(private val game: Game, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult = DrawableResult(
        drawable = GameIconUtils.gameIconBitmap(game)!!.toDrawable(options.context.resources),
        isSampled = false,
        dataSource = DataSource.DISK
    )

    class Factory : Fetcher.Factory<Game> {
        override fun create(data: Game, options: Options, imageLoader: ImageLoader): Fetcher =
            GameIconFetcher(data, options)
    }
}

class GameIconKeyer : Keyer<Game> {
    override fun key(data: Game, options: Options): String = data.path
}

object GameIconUtils {
    /**
     * Decodes [game]'s icon into a 48x48 bitmap. The native layer returns the SMDH large
     * icon (48x48 RGB_565, row-major) packed two pixels per int, so a 48*48/2 (1152)-
     * element [IntArray]; its little-endian bytes are exactly the 2-byte-per-pixel
     * RGB_565 stream a bitmap consumes. Returns null when the game has no icon or the
     * pixel count is unexpected.
     */
    fun gameIconBitmap(game: Game): Bitmap? {
        val pixels = game.icon
            ?: return null
        if (pixels.size != 48 * 48 / 2) {
            return null
        }
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.RGB_565)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(pixels))
        return bitmap
    }

    fun loadGameIcon(activity: FragmentActivity, game: Game, imageView: ImageView) {
        val imageLoader = ImageLoader.Builder(activity)
            .components {
                add(GameIconKeyer())
                add(GameIconFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(activity)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()

        val request = ImageRequest.Builder(activity)
            .data(game)
            .target(imageView)
            .error(R.drawable.no_icon)
            .transformations(
                RoundedCornersTransformation(
                    activity.resources.getDimensionPixelSize(R.dimen.spacing_med).toFloat()
                )
            )
            .build()
        imageLoader.enqueue(request)
    }
}
