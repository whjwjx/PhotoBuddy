package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaAsset

/** 相册页：应用内自定义相册分组（PRD 六·相册 / 5.6 / 8.2.1）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen() {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    val openAlbum = state.albums.firstOrNull { it.id == state.openAlbumId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openAlbum?.name ?: "相册") },
                navigationIcon = {
                    if (openAlbum != null) {
                        TextButton(onClick = { vm.closeAlbum() }) { Text("返回") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            if (openAlbum != null) {
                val items: List<MediaAsset> =
                    state.openAlbumMediaIds.mapNotNull { id ->
                        state.allAssets.firstOrNull { it.id == id }
                    }
                if (items.isEmpty()) {
                    Text("这个相册还没有内容，去整理页用「加入相册」添加吧。")
                } else {
                    Text("共 ${items.size} 项", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items) { a ->
                            Card(Modifier.padding(4.dp)) {
                                Column(Modifier.padding(4.dp)) {
                                    AsyncImage(
                                        model = a.uri,
                                        contentDescription = a.displayName,
                                        modifier = Modifier.fillMaxWidth().height(90.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                    TextButton(onClick = { vm.removeFromAlbum(openAlbum.id, a.id) }) {
                                        Text("移除")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("相册名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    vm.createAlbum(name)
                    name = ""
                }) { Text("新建相册") }

                Spacer(Modifier.height(16.dp))
                if (state.albums.isEmpty()) {
                    Text("还没有相册。")
                } else {
                    LazyColumn {
                        items(state.albums) { a ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            ) {
                                Column(Modifier.fillMaxWidth(0.55f)) {
                                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${state.albumCounts[a.id] ?: 0} 项")
                                }
                                TextButton(onClick = { vm.openAlbum(a.id) }) { Text("查看") }
                            }
                        }
                    }
                }
            }
        }
    }
}
