package de.ka.rgreed.util

import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileUtil {

    @Throws(IOException::class, FileNotFoundException::class)
    fun loadFirebaseConfigurationFile(): FileInputStream {
        val tempPath = Files.createTempFile("resource-", ".json")
        Files.copy(
            ClassLoader.getSystemResourceAsStream(
                "serviceAccountKey.json"
            ),
            tempPath,
            StandardCopyOption.REPLACE_EXISTING
        )

        return FileInputStream(tempPath.toFile())
    }
}