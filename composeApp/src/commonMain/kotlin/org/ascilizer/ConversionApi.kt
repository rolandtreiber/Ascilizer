package org.ascilizer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully

private val httpClient = HttpClient {
    defaultRequest {
        headers.append(HttpHeaders.Accept, "application/json")
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        socketTimeoutMillis = 90_000
    }
}

suspend fun uploadConvertedImage(
    apiUrl: String,
    image: SelectedImage,
    settings: AppSettings,
): Result<String> = runCatching {
    val response = httpClient.post(apiUrl) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("inverted", if (settings.inverted) "1" else "0")
                    append("color", settings.color.apiValue)
                    append("height", settings.size)
                    append(
                        key = "image",
                        value = InputProvider(image.bytes.size.toLong()) {
                            buildPacket {
                                writeFully(image.bytes)
                            }
                        },
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, image.mimeType)
                            append(
                                HttpHeaders.ContentDisposition,
                                "filename=\"${image.fileName}\"",
                            )
                        },
                    )
                },
            ),
        )
    }

    val payload = response.body<String>()
    val message = payload.findJsonValue("message")
    val url = payload.findJsonValue("url")
    if (!response.status.isSuccess() || message != "success" || url.isNullOrBlank()) {
        error(message ?: "Conversion failed.")
    }
    url
}

suspend fun fetchRemoteBytes(url: String): Result<ByteArray> = runCatching {
    httpClient.get(url).body()
}

private fun String.findJsonValue(key: String): String? {
    val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
    return pattern.find(this)?.groupValues?.getOrNull(1)
}
