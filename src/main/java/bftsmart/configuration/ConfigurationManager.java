package bftsmart.configuration;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.tom.util.KeyLoader;

public class ConfigurationManager {
  private final TOMConfiguration staticConf;

  // Dynamic Config, can be changed during runtime
  private int[] currentViewIds;
  private int currentViewF;

  public ConfigurationManager(int procId, KeyLoader loader) {
    this.staticConf = new TOMConfiguration(procId, loader);
    this.currentViewIds = this.staticConf.getInitialView();
  }

  public ConfigurationManager(int procId, String configHome, KeyLoader loader) {
    this.staticConf = new TOMConfiguration(procId, configHome, loader);
    this.currentViewIds = this.staticConf.getInitialView();
  }

  public TOMConfiguration getStaticConf() {
    return staticConf;
  }

  public void setCurrentViewIds(int[] currentViewIds) {
    // TODO Kai: Maybe subscriptions / notification?
    this.currentViewIds = currentViewIds;
  }

  public int[] getCurrentViewIds() {
    return currentViewIds;
  }
}
