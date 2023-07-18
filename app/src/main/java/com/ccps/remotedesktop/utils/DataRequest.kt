package com.ccps.remotedesktop.utils

import com.ccps.remotedesktop.BuildConfig
import com.ccps.remotedesktop.exception.ApiException
import com.ccps.remotedesktop.service.FileService
import com.google.gson.Gson
import com.orhanobut.logger.Logger
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.Okio
import okio.Source
import okio.source
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

class DataRequest : ApiRequest() {
    data class ApiOffer(val method: String, val query: Any, val params: Any, val cid: String)
    data class UploadFile(val path: String, val start: Int, val end: Int, val cid: String)
    data class DownloadFile(val fileInfo: FileInfo, val query: Any, val cid: String) {
        data class FileInfo(
            val name: String,
            val size: Int,
            val lastModified: Int,
            val mimeType: String
        )
    }

    private class FileRequestBody(
        val file: File,
        val start: Long = 0,
        val end: Long = file.length()
    ) : RequestBody() {
        override fun writeTo(sink: BufferedSink) {
            var input:FileInputStream? = null
            var source: Source? = null

            try {
                input = FileInputStream(file)
                if (start > 0) input.skip(start)
                source = input.source()
                sink.writeAll(source)

            } finally {
                input?.close()
                source?.close()
            }
        }

        override fun contentLength(): Long {
            return end - start
        }

        override fun contentType(): MediaType? {
            return "application/octet-stream".toMediaTypeOrNull()
        }
    }

    companion object {
        private const val url = BuildConfig.DATA_API_URL

        fun uploadFile(uploadFile: UploadFile) {
            var file: RandomAccessFile? = null
            var fileSeek: Long = 0
            try {
//                file = RandomAccessFile(uploadFile.path, "r")
                val fileInfo = FileService().getFileInfo(uploadFile.path)
//                val len = uploadFile.end - uploadFile.start
//                val bytes = ByteArray(len)
//
//                fileSeek = file.filePointer
//                file.seek(uploadFile.start.toLong())
//                file.read(bytes)
//
//                val response = request(
//                    "POST",
//                    "${url}/file/upload?_cid=${uploadFile.cid}&_filename=${fileInfo.name}&_filesize=${fileInfo.size}&_file_last_modified=${fileInfo.lastModifyTime}",
//                    bytes
//                )

                val response = request(
                    "POST",
                    "${url}/file/upload?_cid=${uploadFile.cid}&_filename=${fileInfo.name}&_filesize=${fileInfo.size}&_file_last_modified=${fileInfo.lastModifyTime}",
                    FileRequestBody(File(uploadFile.path), uploadFile.start.toLong(),
                        uploadFile.end.toLong()
                    )
                )

                if (!response.isSuccessful) {
                    throw ApiException("${response.code}", response.request, response)
                }
            } catch (e: Exception) {
                Logger.e("upload file exception: ${e.message}")
            } finally {
                file?.seek(fileSeek)
                file?.close()
            }
        }

        fun downloadFile(downloadFile: DownloadFile) {
            data class QueryParams(val save_path: String)

            val queryParams =
                Gson().fromJson(Gson().toJson(downloadFile.query), QueryParams::class.java)
            val file = File(queryParams.save_path)
            val len = if (file.length() >= downloadFile.fileInfo.size) 0 else file.length()

            val response = request(
                "GET",
                "${url}/file/download?_cid=${downloadFile.cid}",
                headers = Headers.headersOf("Range: ${len}-")
            )

            if (!response.isSuccessful) {
                throw ApiException("${response.code}", response.request, response)
            }

            response.body?.let {
                var accessFile: RandomAccessFile? = null
                try {
                    accessFile = RandomAccessFile(file, "rw")
                    accessFile.seek(len)
                    val buffer = ByteArray(2048)
                    var size = it.byteStream().read(buffer)
                    while (size != -1) {
                        accessFile.write(buffer, 0, size)
                        size = it.byteStream().read(buffer)
                    }
                } finally {
                    accessFile?.close()
                }
//
//                val src = File(queryParams.save_path)
//                if (src.exists()) src.delete()
//                file.renameTo(src)
            }
        }

        fun apiAnswer(cid: String, data: Any? = null, headers: Headers? = null) {
            val response = request("POST", "${url}/api/answer?_cid=${cid}", data, headers)
            if (!response.isSuccessful) {
                throw ApiException("${response.code}", response.request, response)
            }
        }

        fun putFiles(apiOffer: ApiOffer) {
            data class QueryParams(val path: String, val offset: Int = 0, val size: Int = 0)

            val queryParams =
                Gson().fromJson(Gson().toJson(apiOffer.query), QueryParams::class.java)
            val filepath = queryParams.path
            val files = FileService().getFiles(filepath, queryParams.offset, queryParams.size)

            apiAnswer(apiOffer.cid, files)
        }
    }
}