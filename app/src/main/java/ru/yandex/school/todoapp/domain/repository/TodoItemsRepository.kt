package ru.yandex.school.todoapp.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.yandex.school.todoapp.domain.model.TodoItem

interface TodoItemsRepository {

    val todoItemsFlow: Flow<List<TodoItem>>

    suspend fun getTodoById(id: String): TodoItem?

    suspend fun saveTodoItem(item: TodoItem)

    suspend fun saveTodoItems(items: List<TodoItem>)

    suspend fun deleteTodoItem(item: TodoItem)
}