package cn.hjmao.lucky;

/**
 * Created by hjmao on 1/12/16.
 */
public class Stage {
  public enum STAGE_STATUS {
    FETCHING,
    OPENING,
    FETCHED,
    OPENED
  }
  private STAGE_STATUS currentStage = STAGE_STATUS.FETCHED;

  public boolean mutex = false;

  private Stage() {}
  private static Stage stageInstance;
  public static Stage getInstance() {
    if (stageInstance == null) {
      stageInstance = new Stage();
    }
    return stageInstance;
  }

  public void entering(STAGE_STATUS stage) {
    stageInstance.currentStage = stage;
    mutex = false;
  }

  public STAGE_STATUS getCurrentStage() {
    return stageInstance.currentStage;
  }
}
