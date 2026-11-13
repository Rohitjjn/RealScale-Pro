package com.tumuyan.ncnn.realsr.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.media.GalleryImage
import com.tumuyan.ncnn.realsr.core.media.GalleryMediaStore
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.PhotoAccessCard
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.rememberImageFilePicker
import com.tumuyan.ncnn.realsr.ui.components.rememberPhotoPermissionRequest
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.SessionPhoto
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gallery — real photos from GalleryMediaStore; tap one to upscale it. */
@Composable
fun GalleryScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionTick = remember { mutableIntStateOf(0) }
    val requestPhotoAccess = rememberPhotoPermissionRequest(onResult = { permissionTick.value++ })
    val hasAccess = remember(permissionTick.value) { GalleryMediaStore.hasReadImages(context) }
    val imagesState = remember { mutableStateOf<List<GalleryImage>?>(null) }
    val images by imagesState

    LaunchedEffect(hasAccess) {
        if (hasAccess && images == null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { GalleryMediaStore.queryRecentImages(context, 200) }
                imagesState.value = loaded
            }
        }
    }

    val albumBucketId by viewModel.albumBucketId.collectAsState()
    val filtered = (images ?: emptyList()).filter {
        albumBucketId == null || it.bucketId == albumBucketId
    }

    val pickImage = rememberImageFilePicker { uri ->
        scope.launch {
            val probed = viewModel.probeSessionPhoto(uri, SessionPhoto.Source.IMPORTED, null)
            if (probed != null) {
                viewModel.setPendingInput(probed)
                navController.navigate(Routes.CONFIG)
            } else {
                viewModel.showToast("This file can't be opened.")
            }
        }
    }

    fun openPhoto(image: GalleryImage) {
        scope.launch {
            val probed = viewModel.probeSessionPhoto(image.uri, SessionPhoto.Source.GALLERY, image.name)
            if (probed != null) {
                viewModel.setPendingInput(probed)
                navController.navigate(Routes.CONFIG)
            } else {
                viewModel.showToast("This photo can't be opened.")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RealScaleIconButton(
                        onClick = { navController.popBackStack() },
                        variant = IconButtonVariant.Dark,
                        contentDescription = "Back",
                        icon = painterResource(R.drawable.ic_lucide_chevron_left),
                    )
                    Text("Gallery", style = RealScaleType.Display24, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    RealScaleIconButton(
                        onClick = pickImage,
                        variant = IconButtonVariant.Dark,
                        contentDescription = "Files",
                        icon = painterResource(R.drawable.ic_lucide_folder_open),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        }

        if (!hasAccess) {
            PhotoAccessCard(onRequestAccess = requestPhotoAccess, modifier = Modifier.padding(20.dp).fillMaxWidth())
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (images == null) {
                    Box(Modifier.fillMaxSize().height(400.dp), contentAlignment = Alignment.Center) {
                        Text("Loading gallery…", style = RealScaleType.Body15, color = c.muted)
                    }
                } else if (filtered.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_lucide_images), contentDescription = null, tint = c.mutedSoft, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No photos found", style = RealScaleType.Display26, color = c.ink)
                        Text("Add some photos to your gallery, then come back.",
                            style = RealScaleType.Body15, color = c.muted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(96.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { image ->
                            Box(
                                modifier = Modifier
                                    .size(width = 96.dp, height = 96.dp)
                                    .clip(RealScaleShape.Lg)
                                    .background(c.card)
                                    .clickable { openPhoto(image) },
                            ) {
                                AsyncImage(
                                    model = image.uri,
                                    contentDescription = image.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
