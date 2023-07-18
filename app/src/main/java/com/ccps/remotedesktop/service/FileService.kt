package com.ccps.remotedesktop.service

import android.webkit.MimeTypeMap
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes


class FileService {
    companion object {
        const val rootPath = "/storage/emulated/0"
    }

    data class FileInfo(
        val name: String,
        val path: String,
        val isFile: Boolean,
        val createTime: Long,
        val lastModifyTime: Long,
        val isLeaf: Boolean,
        val size: Long,
        val mimeType: String,
        val children: List<FileInfo>
    )

    fun getFileInfo(file: File): FileInfo {
        val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        val createTime = attr.creationTime().toMillis()
        val lastModifyTime = attr.lastModifiedTime().toMillis()
        val isLeaf = !(file.isDirectory && file.listFiles()?.isNotEmpty() == true)
        val children = ArrayList<FileInfo>()
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(file.path)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)

        return FileInfo(
            file.name,
            file.path,
            file.isFile,
            createTime,
            lastModifyTime,
            isLeaf,
            attr.size(),
            mimeType.toString(),
            children
        )
    }

    fun getFileInfo(path: String): FileInfo {
        return getFileInfo(File(path))
    }

    fun getFiles(path: String, offset: Int = 0, size: Int = 0): List<FileInfo> {
        val fileList = ArrayList<FileInfo>()
        val directory = File(path)
        val dirFiles = directory.listFiles()

        if (!directory.exists() || !directory.isDirectory || dirFiles == null) {
            return fileList
        }

        // 排序 -> 目录在前
        val files: List<File> = dirFiles.sortedWith(compareBy<File> {
            it.isFile
        }.thenBy {
            it.name
        })

        val end = (if (size > 0) size else files.size) + offset
        files.forEachIndexed lit@ { index, file ->
            // 只获取范围内的文件
            if (index < offset || index > end) return@lit

            val children = ArrayList<FileInfo>()
            val tmpFiles = file.listFiles()
            if (tmpFiles != null) {
                val fileChildren: List<File> = tmpFiles.sortedWith(compareBy<File> {
                    it.isFile
                }.thenBy {
                    it.name
                })

                fileChildren.forEachIndexed lit1@ { i, f ->
                    // 只获取范围内的文件
                    if (i < offset || i > end) return@lit1
                    children.add(getFileInfo(f))
                }
            }

            val fileInfo = getFileInfo(file)
            fileList.add(FileInfo(
                fileInfo.name,
                fileInfo.path,
                fileInfo.isFile,
                fileInfo.createTime,
                fileInfo.lastModifyTime,
                fileInfo.isLeaf,
                fileInfo.size,
                fileInfo.mimeType,
                children
            ))
        }

        return fileList
    }
}