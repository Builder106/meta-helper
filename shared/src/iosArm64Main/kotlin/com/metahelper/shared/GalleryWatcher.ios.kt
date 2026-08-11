package com.metahelper.shared

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSortDescriptor
import platform.Photos.PHAsset
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionSubtypeAlbumRegular
import platform.Photos.PHAssetCollectionSubtypeAny
import platform.Photos.PHAssetCollectionTypeAlbum
import platform.Photos.PHAssetCollectionTypeSmartAlbum
import platform.Photos.PHChange
import platform.Photos.PHFetchOptions
import platform.Photos.PHFetchResult
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserverProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IosGalleryWatcher(
    private val onNewImageDetected: (String) -> Unit
) : GalleryWatcher {
    private var isWatching = false
    private var changeObserver: PhotoLibraryChangeObserver? = null
    private var lastSeenAssetIdentifier: String? = null

    override fun startWatching() {
        if (isWatching) return
        isWatching = true
        println("IosGalleryWatcher: Starting photo library observation")

        changeObserver = PhotoLibraryChangeObserver(onChange = { changeInstance ->
            handlePhotoLibraryChange(changeInstance)
        })

        PHPhotoLibrary.sharedPhotoLibrary().registerChangeObserver(changeObserver!!)

        // Baseline: get the most recent Meta AI asset
        baselineLatestMetaAsset()
    }

    override fun stopWatching() {
        isWatching = false
        println("IosGalleryWatcher: Stopping photo library observation")
        changeObserver?.let {
            PHPhotoLibrary.sharedPhotoLibrary().unregisterChangeObserver(it)
        }
        changeObserver = null
    }

    private fun baselineLatestMetaAsset() {
        val fetchOptions = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false))
            fetchLimit = 1uL
        }

        // Search for albums that might contain Meta AI photos
        val smartAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionTypeSmartAlbum,
            PHAssetCollectionSubtypeAlbumRegular,
            null
        )

        // Also check user albums
        val userAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionTypeAlbum,
            PHAssetCollectionSubtypeAny,
            null
        )

        checkCollectionsForMetaAssets(smartAlbums, fetchOptions)
        checkCollectionsForMetaAssets(userAlbums, fetchOptions)
    }

    private fun checkCollectionsForMetaAssets(collections: PHFetchResult, fetchOptions: PHFetchOptions) {
        val count = collections.count.toInt()
        for (i in 0 until count) {
            val collection = collections.objectAtIndex(i.toULong()) as PHAssetCollection
            val assets = PHAsset.fetchAssetsInAssetCollection(collection, fetchOptions)
            if (assets.count.toInt() > 0) {
                val asset = assets.firstObject as PHAsset
                if (isMetaAsset(asset)) {
                    lastSeenAssetIdentifier = asset.localIdentifier
                    println("IosGalleryWatcher: Baselined to latest Meta asset: $lastSeenAssetIdentifier")
                    return
                }
            }
        }
    }

    private fun isMetaAsset(asset: PHAsset): Boolean {
        // Check if asset is in a "Meta AI" or "Ray-Ban" album/collection
        val collections = PHAssetCollection.fetchAssetCollectionsContainingAsset(
            asset, PHAssetCollectionTypeAlbum, null
        )
        val count = collections.count.toInt()
        for (i in 0 until count) {
            val collection = collections.objectAtIndex(i.toULong()) as PHAssetCollection
            val title = collection.localizedTitle ?: ""
            if (title.contains("Meta AI", ignoreCase = true) ||
                title.contains("Ray-Ban", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun handlePhotoLibraryChange(changeInstance: PHChange) {
        println("IosGalleryWatcher: Photo library change detected")

        // Get details for all changed assets
        val smartAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionTypeSmartAlbum,
            PHAssetCollectionSubtypeAlbumRegular,
            null
        )
        val userAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionTypeAlbum,
            PHAssetCollectionSubtypeAny,
            null
        )

        checkCollectionsForNewMetaAssets(changeInstance, smartAlbums)
        checkCollectionsForNewMetaAssets(changeInstance, userAlbums)
    }

    private fun checkCollectionsForNewMetaAssets(changeInstance: PHChange, collections: PHFetchResult) {
        val count = collections.count.toInt()
        for (i in 0 until count) {
            val collection = collections.objectAtIndex(i.toULong()) as PHAssetCollection
            val assets = PHAsset.fetchAssetsInAssetCollection(collection, null)
            val changeDetails = changeInstance.changeDetailsForFetchResult(assets)

            changeDetails?.let { details ->
                val insertedAssets = details.insertedObjects
                for (obj in insertedAssets.orEmpty()) {
                    val asset = obj as PHAsset
                    if (isMetaAsset(asset) && asset.localIdentifier != lastSeenAssetIdentifier) {
                        println("IosGalleryWatcher: New Meta asset detected: ${asset.localIdentifier}")
                        lastSeenAssetIdentifier = asset.localIdentifier
                        onNewImageDetected(asset.localIdentifier)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoLibraryChangeObserver(
    private val onChange: (PHChange) -> Unit
) : NSObject(), PHPhotoLibraryChangeObserverProtocol {
    override fun photoLibraryDidChange(changeInstance: PHChange) {
        onChange(changeInstance)
    }
}

actual fun createGalleryWatcher(
    context: Any,
    onNewImageDetected: (String) -> Unit
): GalleryWatcher {
    return IosGalleryWatcher { assetIdentifier ->
        onNewImageDetected(assetIdentifier)
    }
}
