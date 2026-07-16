package com.brp.assistant.ui.docs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.entities.UserDocument
import com.brp.assistant.data.repository.UserDocumentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserDocsState(
    val documents: List<UserDocument> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val addedDocName: String? = null
)

@HiltViewModel
class UserDocsViewModel @Inject constructor(
    private val repo: UserDocumentsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UserDocsState())
    val state: StateFlow<UserDocsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val docs = repo.listDocuments()
            _state.value = _state.value.copy(documents = docs, isLoading = false)
        }
    }

    fun addFromUri(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val res = repo.addFromUri(uri, displayName)
            res.onSuccess { doc ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    addedDocName = doc.displayName,
                    documents = repo.listDocuments()
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            refresh()
        }
    }

    fun clearAddedMessage() {
        _state.value = _state.value.copy(addedDocName = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
