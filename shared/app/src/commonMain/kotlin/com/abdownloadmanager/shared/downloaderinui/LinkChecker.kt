package com.abdownloadmanager.shared.downloaderinui

import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import ir.amirab.util.flow.mapStateFlow
import ir.amirab.util.logger.appLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class LinkChecker<
        Credentials : IDownloadCredentials,
        ResponseInfo : IResponseInfo,
        TDownloadSize : DownloadSize,
        >(
    initialCredentials: Credentials
) {
    //input
    val credentials = MutableStateFlow(initialCredentials)

    abstract val suggestedName: StateFlow<String?>
    abstract val downloadSize: StateFlow<TDownloadSize?>

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _responseResult = MutableStateFlow<Result<ResponseInfo>?>(null)
    val responseResult = _responseResult.asStateFlow()
    val responseInfo = _responseResult.mapStateFlow {
        it?.getOrNull()
    }

    private val _isValid = MutableStateFlow(false)
    private val isValid = _isValid.asStateFlow()

    abstract fun infoUpdated(responseInfo: ResponseInfo?)
    abstract suspend fun actualCheck(credentials: Credentials): ResponseInfo
    fun isValidCredentials(credentials: Credentials): Boolean {
        return runCatching {
            credentials.validateCredentials()
            true
        }.getOrElse { false }
    }

    private fun setResult(responseResult: Result<ResponseInfo>?) {
        _responseResult.update { responseResult }
        // update only on success
        infoUpdated(responseResult?.getOrNull())
        validate()
    }

    private fun validate() {
        val isValid = isValidCredentials(this.credentials.value)
        _isValid.update { isValid }
    }

    suspend fun check() {
        val downloadCredentials = credentials.value
        val link = downloadCredentials.link
        val isValidUrl = HttpUrlUtils.isValidUrl(link)
        setResult(null)
        if (link.isBlank() || !isValidUrl) {
            return
        }
        _isLoading.update { true }
        val result = runCatching {
            actualCheck(downloadCredentials)
        }
        _isLoading.update { false }
        result.exceptionOrNull()?.let { e ->
            // a failed check only shows a fallback name in the popup, so without this line the
            // real reason (bot check, timeout, missing binary) never reaches the log
            appLogger.e("Link check failed for $link", e)
        }
        setResult(result)
    }
}
