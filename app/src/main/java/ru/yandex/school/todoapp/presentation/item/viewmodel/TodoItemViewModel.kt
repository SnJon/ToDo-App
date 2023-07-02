package ru.yandex.school.todoapp.presentation.item.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.yandex.school.todoapp.data.model.error.ApiError
import ru.yandex.school.todoapp.data.model.error.DbError
import ru.yandex.school.todoapp.data.model.error.NetworkError
import ru.yandex.school.todoapp.data.model.error.UnknownHostException
import ru.yandex.school.todoapp.domain.model.TodoItem
import ru.yandex.school.todoapp.domain.model.TodoItemPriority
import ru.yandex.school.todoapp.domain.repository.TodoItemsRepository
import ru.yandex.school.todoapp.presentation.base.BaseViewModel
import ru.yandex.school.todoapp.presentation.datetime.model.DateTimeModel
import ru.yandex.school.todoapp.presentation.item.model.TodoItemScreenState
import ru.yandex.school.todoapp.presentation.item.viewmodel.mapper.TodoItemDateMapper
import ru.yandex.school.todoapp.presentation.navigation.AppNavigator
import ru.yandex.school.todoapp.presentation.util.SingleLiveEvent
import ru.yandex.school.todoapp.presentation.util.toDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TodoItemViewModel(
    private val todoItemId: String?,
    private val navigator: AppNavigator,
    private val repository: TodoItemsRepository,
    private val dateMapper: TodoItemDateMapper
) : BaseViewModel() {

    val todoItemScreenState = MutableStateFlow(TodoItemScreenState())

    private val _todoUpdatedLiveData = SingleLiveEvent<Boolean>()
    val todoUpdatedLiveData = _todoUpdatedLiveData

    private val _errorLiveData = MutableLiveData<String>()
    val errorLiveData: LiveData<String> = _errorLiveData

    private var todoItem: TodoItem = runBlocking { loadTodoItem() }

    init {
        loadContent()
    }

    private fun loadContent() {
        todoItemScreenState.update {
            TodoItemScreenState(
                text = todoItem.text,
                priorityRes = todoItem.priority.titleRes,
                deadlineDate = todoItem.deadline?.let { dateMapper.map(it) },
                modifiedDate = todoItem.modifiedAt?.let { dateMapper.map(it) }
            )
        }
    }

    fun updateTodoItemText(text: String) {
        todoItem = todoItem.copy(text = text)
        todoItemScreenState.update { it.copy(text = text) }
    }

    fun updateTodoItemPriority(priority: TodoItemPriority) {
        todoItem = todoItem.copy(priority = priority)
        todoItemScreenState.update { it.copy(priorityRes = priority.titleRes) }
    }

    fun updateDeadlineDate(dateTimeModel: DateTimeModel) {
        val date = (dateTimeModel as? DateTimeModel.Date)?.toDate() ?: return

        todoItem = todoItem.copy(deadline = date)
        todoItemScreenState.update { it.copy(deadlineDate = dateMapper.map(date)) }
    }

    fun onDeadlineDateActivate(isActive: Boolean) {
        if (isActive) {
            val deadlineDate = todoItem.deadline ?: LocalDate.now()
            val deadlineDateUi = deadlineDate?.let { dateMapper.map(it) }

            todoItem = todoItem.copy(deadline = deadlineDate)
            todoItemScreenState.update { it.copy(deadlineDate = deadlineDateUi) }
        } else {
            todoItem = todoItem.copy(deadline = null)
            todoItemScreenState.update { it.copy(deadlineDate = null) }
        }
    }

    private suspend fun loadTodoItem(): TodoItem {
        return if (todoItemId == null) {
            TodoItem.empty
        } else {
            repository.getTodoById(todoItemId) ?: TodoItem.empty
        }
    }

    fun addTodoItem() {
        val isCreating = todoItemId == null
        val currentDate = LocalDate.now()
        val currentDateTime = LocalDateTime.now()

        val upsertJob = launchJob(
            onError = { handleAppError(it) }
        )
        {
            if (isCreating) {
                Log.e("todo", "creating")
                repository.addTodoItem(
                    todoItem.copy(
                        id = UUID.randomUUID().toString(),
                        createAt = currentDate,
                        modifiedAt = currentDateTime
                    )
                )
            } else {
                Log.e("todo", "changing")
                repository.updateTodoItem(
                    todoItem.copy(
                        modifiedAt = currentDateTime,
                        isSync = false
                    )
                )
            }
        }
        onWaitCompleteJob(upsertJob)
    }

    // Жёсткий костыль! Буду рад, если поможете исправить
    // Проблема в том, что если вовзращаемся на TodoListFragment без ожидания, то запрос не успевает выполниться
    // Если делаем в репозитории отдельную Job с join(), тогда падает приложение в случае Exception (например при отсутствии интернета)
    private fun onWaitCompleteJob(job: Job) {
        viewModelScope.launch {
            while (true) {
                delay(200)
                _todoUpdatedLiveData.postValue(job.isCompleted)
            }
        }
    }

    fun closeTodoItem() {
        navigator.backTodoList()
    }

    fun deleteTodoItem() {
        launchJob(
            onError = { }
        ) {
            repository.deleteTodoItem(todoItem)
        }
    }

    private fun handleAppError(error: Throwable) {
        val errorMessage = when (error) {
            is NetworkError -> "Ошибка сети"
            is UnknownHostException -> "Ошибка сети"
            is DbError -> "Ошибка базы данных"
            is ApiError -> "Ошибка API: ${error.status} ${error.code}"
            else -> "Отсутствует соединение с интернетом"
        }

        _errorLiveData.postValue(errorMessage)
    }
}