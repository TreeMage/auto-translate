package extraction.processor;

import extraction.ExtractionResult;

import java.nio.file.Path;

public interface ExtractionProcessor
{

  ExtractionResult process(Path searchDirectory);

  String getProjectTypeSupport();
}
