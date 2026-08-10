package com.metahelper.shared

import kotlinx.coroutines.*
import platform.Foundation.NSArray
import platform.Foundation.NSError
import platform.Foundation.NSObject
import platform.Foundation.NSString
import platform.Photos.PHAsset
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionSubtype
import platform.Photos.PHAssetCollectionType
import platform.Photos.PHChange
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHObjectChangeDetails
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserver

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

        PHPhotoLibrary.shared().registerChangeObserver(changeObserver!!)

        // Baseline: get the most recent Meta AI asset
        baselineLatestMetaAsset()
    }

    override fun stopWatching() {
        isWatching = false
        println("IosGalleryWatcher: Stopping photo library observation")
        changeObserver?.let {
            PHPhotoLibrary.shared().unregisterChangeObserver(it)
        }
        changeObserver = null
    }

    private fun baselineLatestMetaAsset() {
        val fetchOptions = PHFetchOptions().apply {
            sortDescriptors = NSArray(arrayOf(
                platform.Foundation.NSSortDescriptor("creationDate", false)
            ))
            fetchLimit = 1
        }

        // Search for albums that might contain Meta AI photos
        val smartAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionType.SmartAlbum,
            subtype: PHAssetCollectionSubtype.AlbumRegular,
            options: nil
        )

        // Also check user albums
        val userAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionType.Album,
            subtype: PHAssetCollectionSubtype.Any,
            options: nil
        )

        checkCollectionsForMetaAssets(smartAlbums, fetchOptions)
        checkCollectionsForMetaAssets(userAlbums, fetchOptions)
    }

    private fun checkCollectionsForMetaAssets(collections: platform.Photos.PHFetchResult<PHAssetCollection>, fetchOptions: PHFetchOptions) {
        for (i in 0 until collections.count().toInt()) {
            val collection = collections.objectAtIndex(i)
            val assets = PHAsset.fetchAssetsInAssetCollection(collection, options: fetchOptions)
            if (assets.count() > 0) {
                val asset = assets.firstObject() as PHAsset
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
            asset, type: PHAssetCollectionType.Album, options: nil
        )
        for (i in 0 until collections.count().toInt()) {
            val collection = collections.objectAtIndex(i)
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
            PHAssetCollectionType.SmartAlbum,
            subtype: PHAssetCollectionSubtype.AlbumRegular,
            options: nil
        )
        val userAlbums = PHAssetCollection.fetchAssetCollectionsWithType(
            PHAssetCollectionType.Album,
            subtype: PHAssetCollectionSubtype.Any,
            options: nil
        )

        checkCollectionsForNewMetaAssets(changeInstance, smartAlbums)
        checkCollectionsForNewMetaAssets(changeInstance, userAlbums)
    }

    private fun checkCollectionsForNewMetaAssets(changeInstance: PHChange, collections: platform.Photos.PHFetchResult<PHAssetCollection>) {
        for (i in 0 until collections.count().toInt()) {
            val collection = collections.objectAtIndex(i)
            val changeDetails = changeInstance.changeDetailsForFetchResult(
                PHAsset.fetchAssetsInAssetCollection(collection, options: nil)
            )

            changeDetails?.let { details ->
                val insertedAssets = details.insertedObjects
                for (obj in insertedAssets) {
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

private class PhotoLibraryChangeObserver(
    private val onChange: (PHChange) -> Unit
) : NSObject(), PHPhotoLibraryChangeObserver {
    override fun photoLibraryDidChange(changeInstance: PHChange) {
        onChange(changeInstance)
    }
}

actual fun createGalleryWatcher(
    context: Any,
    onNewImageDetected: (Any) -> Unit
): GalleryWatcher {
    return IosGalleryWatcher { assetIdentifier ->
        onNewImageDetected(assetIdentifier)
    }
}