package cn.hjmao.lucky;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by hjmao on 1/12/16.
 */
public class HongbaoService extends AccessibilityService {
  private static NotificationBuffer NOTIFICATION_BUFFER = new NotificationBuffer();

  private static String HONGBAO_DETAILS_EN = "Details";
  private static String HONGBAO_DETAILS_CH = "红包详情";
  private static String HONGBAO_BETTER_LUCK_EN = "Better luck next time!";
  private static String HONGBAO_BETTER_LUCK_CH = "手慢了";
  private static String HONGBAO_EXPIRED_CH = "过期";
  private static String HONGBAO_EXPIRED_EN = "Expired";
  private static String HONGBAO_OPEN_EN = "Open";
  private static String HONGBAO_OPENED_EN = "You've opened";
  private static String HONGBAO_OPEN_CH = "拆红包";
  private static String HONGBAO_VIEW_SELF_CH = "查看红包";
  private static String HONGBAO_VIEW_OTHERS_CH = "领取红包";
  private static String HONGBAO_DEFAULT_TEXT_EN = "Best wishes!";
  private static String HONGBAO_DEFAULT_TEXT_CH = "恭喜发财,大吉大利!";
  private static String HONGBAO_NOTIFICATION_TIP = "[微信红包]";

  private static String[] OPEN_NODE_TEXT = new String[] {HONGBAO_OPEN_EN, HONGBAO_OPEN_CH};
  private static String[] OPENED_NODE_TEXT =  new String[] {HONGBAO_DETAILS_CH, HONGBAO_BETTER_LUCK_CH, HONGBAO_DETAILS_EN, HONGBAO_BETTER_LUCK_EN};
  private static String[] FAILED_NODE_TEXT =  new String[] {HONGBAO_BETTER_LUCK_EN, HONGBAO_DETAILS_EN, HONGBAO_EXPIRED_EN, HONGBAO_BETTER_LUCK_CH, HONGBAO_DETAILS_CH, HONGBAO_EXPIRED_CH};
  private static String[] FETCH_NODE_TEXT = new String[] {HONGBAO_VIEW_OTHERS_CH};

  private int ttl = 0;
  private static final int MAX_TTL = 24;
  private List<AccessibilityNodeInfo> nodesToFetch = new ArrayList<>();
  private List<String> fetchedIdentifiers = new ArrayList<>();

  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
      handleNotificationChange(accessibilityEvent);
      return;
    } else {
      if (Stage.getInstance().mutex) {
        return;
      }
      Stage.getInstance().mutex = true;
      try {
        handleWindowChange(accessibilityEvent.getSource());
      } finally {
        Stage.getInstance().mutex = false;
        Notification notification = NOTIFICATION_BUFFER.take();
        if (notification != null) {
          try {
            notification.contentIntent.send();
          } catch (PendingIntent.CanceledException ce) {
          }
        }
      }
    }
  }

  @Override
  public void onInterrupt() {
  }

  private void handleNotificationChange(AccessibilityEvent accessibilityEvent) {
    String tip = accessibilityEvent.getText().toString();
    if (!tip.contains(HONGBAO_NOTIFICATION_TIP)) {
      return;
    }
    Parcelable parcelable = accessibilityEvent.getParcelableData();
    if (parcelable instanceof Notification) {
      Notification notification = (Notification) parcelable;
      try {
        if (!Stage.getInstance().mutex) {
          notification.contentIntent.send();
        } else {
          try {
            NOTIFICATION_BUFFER.put(notification);
          } catch (InterruptedException ie) {
            ie.printStackTrace();
          }
        }
      } catch (PendingIntent.CanceledException e) {
      }
    }
    return;
  }

  private void performMyGlobalAction(int action) {
    Stage.getInstance().mutex = false;
    performGlobalAction(action);
  }

  private void handleWindowChange(AccessibilityNodeInfo node) {
    switch (Stage.getInstance().getCurrentStage()) {
      case OPENING:
        if (openHongbao(node) == -1 && ttl < MAX_TTL) {
          return;
        }

        ttl = 0;
        Stage.getInstance().entering(Stage.STAGE_STATUS.FETCHED);
        performMyGlobalAction(GLOBAL_ACTION_BACK);
        if (nodesToFetch.size() == 0) {
          handleWindowChange(node);
        }
        break;
      case OPENED:
        List<AccessibilityNodeInfo> successNodes = findAccessibilityNodeInfosByTexts(node, OPENED_NODE_TEXT);
        if (successNodes.isEmpty() && ttl < MAX_TTL) {
          ttl += 1;
          return;
        }
        ttl = 0;
        Stage.getInstance().entering(Stage.STAGE_STATUS.FETCHED);
        performMyGlobalAction(GLOBAL_ACTION_BACK);
        break;
      case FETCHED:
        if (nodesToFetch.size() > 0) {
          AccessibilityNodeInfo nextNode = nodesToFetch.remove(nodesToFetch.size() - 1);
          if (nextNode.getParent() != null) {
            String id = getHongbaoHash(nextNode);
            if (id == null) {
              return;
            }
            fetchedIdentifiers.add(id);

            Stage.getInstance().entering(Stage.STAGE_STATUS.OPENING);
            nextNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
          }
          return;
        }

        Stage.getInstance().entering(Stage.STAGE_STATUS.FETCHING);
        fetchHongbao(node);
        Stage.getInstance().entering(Stage.STAGE_STATUS.FETCHED);

        break;
    }
  }

  private void fetchHongbao(AccessibilityNodeInfo node) {
    if (node == null) {
      return;
    }
    List<AccessibilityNodeInfo> fetchNodes = findAccessibilityNodeInfosByTexts(node, FETCH_NODE_TEXT);
    if (fetchNodes.isEmpty()) {
      return;
    }

    for (AccessibilityNodeInfo cellNode : fetchNodes) {
      String id = getHongbaoHash(cellNode);
      if (id != null && !fetchedIdentifiers.contains(id)) {
        nodesToFetch.add(cellNode);
      }
    }
  }

  private int openHongbao(AccessibilityNodeInfo node) {
    if (node == null) {
      return -1;
    }
    List<AccessibilityNodeInfo> failureNoticeNodes = findAccessibilityNodeInfosByTexts(node, FAILED_NODE_TEXT);
    if (!failureNoticeNodes.isEmpty()) {
      return 0;
    }
    List<AccessibilityNodeInfo> successNoticeNodes = findAccessibilityNodeInfosByTexts(node, OPEN_NODE_TEXT);
    if (!successNoticeNodes.isEmpty()) {
      AccessibilityNodeInfo openNode = successNoticeNodes.get(successNoticeNodes.size() - 1);
      Stage.getInstance().entering(Stage.STAGE_STATUS.OPENED);
      openNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
      return 0;
    } else {
      Stage.getInstance().entering(Stage.STAGE_STATUS.OPENING);
      ttl += 1;
      return -1;
    }
  }

  private String getHongbaoHash(AccessibilityNodeInfo node) {
    String content;
    try {
      AccessibilityNodeInfo i = node.getParent().getChild(0);
      content = i.getText().toString();
    } catch (NullPointerException npe) {
      return null;
    }

    return content + "@" + getAccessibilityNodeId(node);
  }

  private String getAccessibilityNodeId(AccessibilityNodeInfo node) {
    Pattern objHashPattern = Pattern.compile("(?<=@)[0-9|a-z]+(?=;)");
    Matcher objHashMatcher = objHashPattern.matcher(node.toString());
    objHashMatcher.find();

    return objHashMatcher.group(0);
  }
  private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
    List<AccessibilityNodeInfo> nodes = new ArrayList<AccessibilityNodeInfo>();
    for (String text : texts) {
      if (text == null || "".equals(text.trim())) {
        continue;
      }
      List<AccessibilityNodeInfo> thisTextNodes = nodeInfo.findAccessibilityNodeInfosByText(text);
      if (!thisTextNodes.isEmpty()) {
        nodes.addAll(thisTextNodes);
        break;
      }
    }
    return nodes;
  }
}
