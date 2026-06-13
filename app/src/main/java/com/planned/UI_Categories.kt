package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Categories(db: AppDatabase) {
    when (categoriesCurrentView) {
        "list" -> CategoriesList(
            db = db,
            onCategoryClick = { category ->
                categoriesSelectedCategory = category
                categoriesCurrentView = "info"
            }
        )
        "info" -> categoriesSelectedCategory?.let { category ->
            CategoryInfoPage(
                db = db,
                category = category,
                onBack = {
                    categoriesCurrentView = "list"
                    categoriesSelectedCategory = null
                },
                onUpdate = { categoriesCurrentView = "update" }
            )
        }
        "update" -> categoriesSelectedCategory?.let { category ->
            CategoryUpdateForm(
                db = db,
                category = category,
                onBack = { categoriesCurrentView = "info" },
                onSaveSuccess = { updatedCategory ->
                    categoriesSelectedCategory = updatedCategory
                    categoriesCurrentView = "info"
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoriesList(
    db: AppDatabase,
    onCategoryClick: (Category) -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(16.dp)
    ) {
        Text(
            "Categories",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
        )

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No categories",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    CategoryListItem(
                        category = category,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryListItem(
    category: Category,
    onClick: () -> Unit
) {
    val categoryColor = Converters.toColor(category.color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(INNER_CIRCLE_SIZE)
                .background(categoryColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = category.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoryInfoPage(
    db: AppDatabase,
    category: Category,
    onBack: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var currentCategory by remember { mutableStateOf(category) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventCount by remember { mutableIntStateOf(0) }
    var deadlineCount by remember { mutableIntStateOf(0) }
    var taskCount by remember { mutableIntStateOf(0) }
    var reminderCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(category.id) {
        currentCategory = db.categoryDao().getCategoryById(category.id) ?: category

        // Related items
        val events = db.eventDao().getAllMasterEvents().filter { it.categoryId == category.id }
        val deadlines = db.deadlineDao().getAll().filter { it.categoryId == category.id }
        val tasks = db.taskDao().getAllMasterTasks().filter { it.categoryId == category.id && it.status != 3 }
        val reminders = db.reminderDao().getAllMasterReminders().filter { it.categoryId == category.id }

        eventCount = events.size
        deadlineCount = deadlines.size
        taskCount = tasks.size
        reminderCount = reminders.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = PrimaryColor,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() }
                .size(40.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentCategory.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentCategory.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentCategory.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Color display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Color: ", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Converters.toColor(currentCategory.color))
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Usage statistics
            InfoCard(listOf(
                "Events" to eventCount.toString(),
                "Deadlines" to deadlineCount.toString(),
                "Reminders" to reminderCount.toString(),
                "Tasks" to taskCount.toString()
            ))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Delete", fontSize = 16.sp, color = Color.White)
            }
            Button(
                onClick = onUpdate,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Update", fontSize = 16.sp, color = Color.White)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = BackgroundColor,
                title = null,
                text = { Text("Delete this category?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                scope.launch {
                                    CategoryManager.delete(context, db, currentCategory.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Delete", fontSize = 12.sp, color = Color.White) }
                        Button(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Cancel", fontSize = 12.sp, color = Color.White) }
                    }
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoryUpdateForm(
    db: AppDatabase,
    category: Category,
    onBack: () -> Unit,
    onSaveSuccess: (Category) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(category.title) }
    var notes by remember { mutableStateOf(category.notes ?: "") }
    var color by remember { mutableStateOf(Converters.toColor(category.color)) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                CategoryForm(
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    color = color,
                    onColorChange = { color = it },
                    resetTrigger = resetTrigger
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            title = category.title
                            notes = category.notes ?: ""
                            color = Converters.toColor(category.color)
                            resetTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Reset", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                scope.launch {
                                    showNotification = true
                                    scrollState.animateScrollTo(0)
                                    delay(3000)
                                    showNotification = false
                                }
                                return@Button
                            }
                            scope.launch {
                                val updatedCategory = category.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    color = Converters.fromColor(color)
                                )
                                CategoryManager.update(db, updatedCategory)
                                val refreshedCategory = db.categoryDao().getCategoryById(category.id) ?: updatedCategory
                                onSaveSuccess(refreshedCategory)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Save", fontSize = 16.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNotification,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val dragOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .offset(y = dragOffset.floatValue.coerceAtMost(0f).dp)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            dragOffset.floatValue += delta
                            if (dragOffset.floatValue < -80f) showNotification = false
                        },
                        onDragStopped = { dragOffset.floatValue = 0f }
                    )
            ) {
                Surface(
                    color = PrimaryColor,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shadowElevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Title is required", color = BackgroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}