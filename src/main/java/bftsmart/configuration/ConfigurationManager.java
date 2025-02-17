package bftsmart.configuration;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.tom.util.KeyLoader;

public class ConfigurationManager {
  private final TOMConfiguration staticConf;
  private int currentViewF;

  public ConfigurationManager(int procId, KeyLoader loader) {
    this.staticConf = new TOMConfiguration(procId, loader);
  }

  public ConfigurationManager(int procId, String configHome, KeyLoader loader) {
    this.staticConf = new TOMConfiguration(procId, configHome, loader);
  }

  public TOMConfiguration getStaticConf() {
    return staticConf;
  }

}
