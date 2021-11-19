import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CsvReader {
  private final BufferedReader bufferedReader;
  private final String delimiter = ",";

  public CsvReader(File file) throws FileNotFoundException {
    this.bufferedReader = new BufferedReader(new FileReader(file));
  }

  public List<String> readline() throws IOException {
    String line = bufferedReader.readLine();
    if (line == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(line.replace(delimiter, delimiter + " ").split(delimiter))
        .map(String::trim)
        .map(value -> value.isBlank() ? "N/A" : value)
        .collect(Collectors.toList());
  }

  public void close() throws IOException {
    this.bufferedReader.close();
  }
}
