package nl.carosi.remarkablepocket;

import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
  private static final String VERSION_PROPERTIES = "version.properties";

  @Override
  public String[] getVersion() throws Exception {
    Properties properties = new Properties();
    try (InputStream input =
        VersionProvider.class.getClassLoader().getResourceAsStream(VERSION_PROPERTIES)) {
      if (input != null) {
        properties.load(input);
        return new String[] {properties.getProperty("version")};
      } else {
        throw new IllegalStateException("Unable to find " + VERSION_PROPERTIES + " in classpath");
      }
    }
  }
}
