package extraction;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileContentReader
{

    private FileContentReader()
    {
    }

    public static String tryReadContent(Path filePath)
    {
        Path decodedFilePath = null;
        try
        {
            decodedFilePath = Paths.get(URLDecoder.decode(String.valueOf(Paths.get(String.valueOf(filePath))), StandardCharsets.UTF_8));
            return Files.readString(decodedFilePath, StandardCharsets.UTF_8);
        } catch (IOException e)
        {
        }
        return "";
    }

    public static String transformTextToOneLine(String fileContent)
    {
        fileContent = fileContent.replace("\n", " ");
        fileContent = fileContent.replaceAll("\\s+", " ");
        return fileContent;
    }

}