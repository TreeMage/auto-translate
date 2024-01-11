package extraction.processor;

import extraction.ExtractionResult;
import extraction.files.JavaScriptAndTypeScriptFilesFinder;
import extraction.keys.IEighteenNextKeyExtractor;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IEighteenNextProcessor implements ExtractionProcessor
{

  @Override
  public ExtractionResult process(Path searchDirectory)
  {
    JavaScriptAndTypeScriptFilesFinder filesFinder = new JavaScriptAndTypeScriptFilesFinder();
    IEighteenNextKeyExtractor keyExtractor = new IEighteenNextKeyExtractor();

    List<Path> foundFiles = filesFinder.findFilesToProcess(searchDirectory);

    Set<String> keys = new HashSet<>();
    for (Path file : foundFiles)
    {
      Set<String> batchKeys = keyExtractor.extractKeysFromFile(file);
      keys.addAll(batchKeys);
    }
    return ExtractionResult.of(keys, foundFiles);
  }

  @Override
  public String getProjectTypeSupport() {
    return "i18next/i18next";
  }
}
