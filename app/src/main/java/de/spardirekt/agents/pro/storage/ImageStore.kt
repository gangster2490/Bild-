package de.spardirekt.agents.pro.storage

import android.content.Context
import android.net.Uri
import de.spardirekt.agents.pro.model.ProjectImage
import java.io.File

object ImageStore {

    fun persist(context: Context, projectId: String, imageId: String, source: Uri): ProjectImage {
        val dir = projectDir(context, projectId)
        dir.mkdirs()
        val dest = File(dir, "$imageId.jpg")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot read image")
        return ProjectImage(
            id = imageId,
            uri = Uri.fromFile(dest).toString(),
            localPath = dest.absolutePath
        )
    }

    fun deleteImage(context: Context, projectId: String, imageId: String) {
        File(projectDir(context, projectId), "$imageId.jpg").delete()
    }

    fun deleteProject(context: Context, projectId: String) {
        projectDir(context, projectId).deleteRecursively()
    }

    fun deleteAll(context: Context) {
        File(context.filesDir, "projects").deleteRecursively()
    }

    private fun projectDir(context: Context, projectId: String): File {
        return File(context.filesDir, "projects/$projectId")
    }
}
