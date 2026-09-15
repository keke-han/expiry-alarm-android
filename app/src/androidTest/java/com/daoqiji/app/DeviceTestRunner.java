package com.daoqiji.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.service.notification.StatusBarNotification;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Local instrumentation only; never included in the distributed APK. */
public class DeviceTestRunner extends Instrumentation {
    private Bundle args;
    private Activity activity;
    private final List<String> evidence = new ArrayList<>();
    private final List<String> createdIds = new ArrayList<>();
    private ExpiryRepository repository;
    private SettingsRepository settingsRepository;
    private AppSettings originalSettings;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            repository = new ExpiryRepository(getTargetContext());
            settingsRepository = new SettingsRepository(getTargetContext());
            originalSettings = settingsRepository.load();
            settingsRepository.save(new AppSettings(
                originalSettings.getTheme(),
                originalSettings.getDefaultRemindBeforeDays(),
                originalSettings.getReminderTime(),
                true
            ));
            SystemClock.sleep(150);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getUiAutomation().grantRuntimePermission(
                    getTargetContext().getPackageName(), Manifest.permission.POST_NOTIFICATIONS
                );
            }
            String scenario = args.getString("scenario", "core");
            if (scenario.equals("cleanup")) {
                cleanupKnownTestItems();
            } else if (scenario.equals("scheduledSetup")) {
                testScheduledReminderSetup();
            } else if (scenario.equals("scheduledVerify")) {
                testScheduledReminderVerify();
            } else if (scenario.equals("receiver")) {
                testReceiver();
            } else if (scenario.equals("parser")) {
                openHome();
                testParserRegressions();
            } else if (scenario.equals("editing")) {
                openHome();
                testEditing();
            } else if (scenario.equals("lifecycle")) {
                openHome();
                testLifecycleAndScreenshot();
            } else {
                openHome();
                testCoreAndScreenshots();
            }
            result.putString("result", "PASS");
        } catch (Throwable failure) {
            code = Activity.RESULT_CANCELED;
            result.putString("result", "FAIL: " + failure);
            evidence.add("FAIL: " + failure);
            try { capture("failure"); } catch (Throwable ignored) { }
        } finally {
            if (repository != null) for (String id : createdIds) repository.delete(id);
            if (originalSettings != null) settingsRepository.save(originalSettings);
            if (activity != null) runOnMainSync(() -> activity.finish());
            if (originalSettings != null) ReminderScheduler.INSTANCE.scheduleDailyCheck(getTargetContext());
            SystemClock.sleep(1200);
        }
        try {
            File report = new File(getTargetContext().getExternalFilesDir(null),
                "report-" + args.getString("scenario", "core") + ".txt");
            try (FileOutputStream stream = new FileOutputStream(report)) {
                stream.write(String.join("\n", evidence).getBytes(StandardCharsets.UTF_8));
            }
            result.putString("report", report.getAbsolutePath());
        } catch (Exception failure) { result.putString("reportError", failure.toString()); }
        result.putString("checks", String.join("\n", evidence));
        finish(code, result);
    }

    private void openHome() {
        if (activity != null) runOnMainSync(() -> activity.finish());
        Intent launch = new Intent(getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = startActivitySync(launch);
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (find(root, n -> "添加到期提醒".contentEquals(value(n.getText()))) != null) return;
            if (find(root, n -> "设置".contentEquals(value(n.getText()))) != null) {
                runOnMainSync(() -> ((MainActivity) activity).getOnBackPressedDispatcher().onBackPressed());
            }
            SystemClock.sleep(200);
        }
        throw new AssertionError("等待首页超时");
    }

    private void testCoreAndScreenshots() throws Exception {
        getTargetContext().getSystemService(NotificationManager.class).cancelAll();
        click("设置");
        await(n -> "设置".contentEquals(value(n.getText())));
        scrollToTop();
        click("浅色");
        click("默认提前提醒");
        field(0, "7");
        click("保存");
        check(settingsRepository.load().getDefaultRemindBeforeDays() == 7, "默认提前天数保存为7");
        click("返回");
        enter("身份证2030年1月1日到期");
        await(n -> "7天".contentEquals(value(n.getText())));
        pass("中文输入未写提前时间时采用设置中的7天");
        click("清空输入");
        enter("护照2028年7月到期，提前陆个月提醒");
        ParsedExpiry monthResult = ExpiryParser.INSTANCE.parse("护照2028年7月到期，提前陆个月提醒", 7);
        check(monthResult != null && monthResult.getExpireDate().equals("2028-07-31") &&
            monthResult.getRemindBeforeDays() == 180, "年月和大写数字解析结果正确");
        pass("只输入年月时采用当月最后一天，大写陆个月识别为180天");
        click("清空输入");
        enter("护照2028年7月8日到期，提前六个月提醒");
        ParsedExpiry chineseResult = ExpiryParser.INSTANCE.parse("护照2028年7月8日到期，提前六个月提醒", 7);
        check(chineseResult != null && chineseResult.getRemindBeforeDays() == 180,
            "中文六个月解析结果正确");
        pass("中文六个月识别为180天");
        click("清空输入");
        enter("护照2028年7月8日到期，提前半年提醒");
        ParsedExpiry halfYearResult = ExpiryParser.INSTANCE.parse("护照2028年7月8日到期，提前半年提醒", 7);
        check(halfYearResult != null && halfYearResult.getRemindBeforeDays() == 180,
            "半年解析结果正确");
        pass("半年识别为180天");
        click("清空输入");
        enter("护照2029年5月10日到期，提前90天、30天和7天提醒");
        awaitWithScroll(n -> "90天 / 30天 / 7天".contentEquals(value(n.getText())));
        capture("01-sentence");
        addCurrent("护照", 90);
        check(repository.getAll().stream().anyMatch(i -> i.getTitle().equals("护照") &&
            i.getAdditionalRemindBeforeDays().equals(List.of(30, 7))), "多个提前提醒保存成功");

        openHome();
        LocalDate today = LocalDate.now();
        enter("健身会员" + chinese(today.plusDays(45)) + "到期，提前7天提醒");
        addCurrent("健身会员", 7);
        openHome();
        enter("酸奶" + chinese(today.plusDays(5)) + "到期，提前1天提醒");
        addCurrent("酸奶", 1);
        openHome();
        check(repository.getAll().stream().anyMatch(i -> i.getTitle().equals("护照") &&
            i.getRemindBeforeDays() == 90), "重新打开后记录和明确的提前时间保留");
        awaitWithScroll(n -> n.isVisibleToUser() && "酸奶".contentEquals(value(n.getText())));
        capture("02-list-light");
        click("日历");
        await(n -> value(n.getText()).matches("2026年[0-9]+月"));
        capture("06-calendar");
        click("列表");
        field(1, "酸奶");
        await(n -> "酸奶".contentEquals(value(n.getText())));
        capture("07-search");
        openHome();
        click("酸奶");
        field(2, "2");
        capture("03-edit");
        click("保存");
        check(repository.getAll().stream().anyMatch(i -> i.getTitle().equals("酸奶") &&
            i.getRemindBeforeDays() == 2), "编辑提醒天数保存成功");
        click("设置");
        await(n -> "设置".contentEquals(value(n.getText())));
        scrollToTop();
        click("深色");
        check(settingsRepository.load().getTheme() == ThemeChoice.Dark, "深色主题持久化成功");
        click("返回");
        openHome();
        capture("04-list-dark");
        click("设置");
        await(n -> "设置".contentEquals(value(n.getText())));
        scrollToTop();
        click("浅色");
        capture("05-settings");
        click("隐私说明");
        await(n -> value(n.getText()).contains("achilles042178@outlook.com"));
        pass("隐私说明包含开发者和联系邮箱");
        click("关闭");
        click("意见反馈");
        await(n -> value(n.getText()).contains("achilles042178@outlook.com"));
        pass("意见反馈显示开发者和反馈邮箱");
        click("关闭");
        click("返回");
        click("酸奶");
        click("删除");
        check(repository.getAll().stream().noneMatch(i -> createdIds.contains(i.getId()) &&
            i.getTitle().equals("酸奶")), "删除测试事项成功");
    }

    private void testParserRegressions() {
        enter("护照2028年7月到期，提前陆个月提醒");
        await(n -> "2028-07-31".contentEquals(value(n.getText())));
        await(n -> "180天".contentEquals(value(n.getText())));
        pass("只输入年月时采用当月最后一天，大写陆个月识别为180天");
        click("清空输入");
        enter("护照2028年7月8日到期，提前六个月提醒");
        await(n -> "180天".contentEquals(value(n.getText())));
        pass("中文六个月识别为180天");
        click("清空输入");
        enter("护照2028年7月8日到期，提前半年提醒");
        await(n -> "180天".contentEquals(value(n.getText())));
        pass("半年识别为180天");
        click("清空输入");
        enter("信用卡2027年11月20号到期，提前1个月提醒");
        await(n -> "信用卡".contentEquals(value(n.getText())));
        await(n -> "2027-11-20".contentEquals(value(n.getText())));
        await(n -> "30天".contentEquals(value(n.getText())));
        pass("口语日期后缀“号”识别为具体日期且不混入事项名称");
        click("清空输入");
        enter("妈妈生日2027年5月20日提前1周提醒");
        await(n -> "妈妈生日".contentEquals(value(n.getText())));
        await(n -> "2027-05-20".contentEquals(value(n.getText())));
        await(n -> "7天".contentEquals(value(n.getText())));
        pass("提前一周识别为7天且不混入事项名称");
    }

    private void testEditing() {
        String title = "验收测试" + SystemClock.uptimeMillis();
        enter(title + "，2030年12月到期提前六个月提醒");
        addCurrent(title, 180);
        String id = createdIds.get(createdIds.size() - 1);
        click(title);
        field(0, title + "已编辑");
        field(1, "2031-01-15");
        field(2, "30");
        click("保存");
        openHome();
        check(repository.getAll().stream().anyMatch(i -> i.getId().equals(id) &&
            i.getTitle().equals(title + "已编辑") && i.getExpireDate().equals("2031-01-15") &&
            i.getRemindBeforeDays() == 30), "编辑事项、日期、提前天数后重新打开仍保留");
        click(title + "已编辑");
        click("删除");
        openHome();
        check(repository.getAll().stream().noneMatch(i -> i.getId().equals(id)),
            "删除专用测试记录后重新打开仍已删除");
    }

    private void testLifecycleAndScreenshot() throws Exception {
        String title = "护照续期演示";
        enter(title + "2030年10月20日到期，提前90天、30天和7天提醒");
        addCurrent(title, 90);
        String id = createdIds.get(createdIds.size() - 1);
        openHome();
        click(title);
        click("已处理");
        openHome();
        click("已处理");
        click(title);
        field(1, "2035-10-20");
        click("续期");
        openHome();
        click(title);
        awaitWithScroll(n -> value(n.getText()).startsWith("续期记录："));
        capture("08-renewal-history");
        ExpiryItem renewed = repository.getAll().stream().filter(i -> i.getId().equals(id))
            .findFirst().orElseThrow(() -> new AssertionError("续期事项不存在"));
        check(renewed.getExpireDate().equals("2035-10-20") &&
            renewed.getRenewalHistory().contains("2030-10-20"), "已处理事项续期并保留历史日期");
        click("删除");
    }

    private void testReceiver() throws Exception {
        NotificationHelper.INSTANCE.ensureChannel(getTargetContext());
        NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
        ExpiryItem today = repository.add(new ParsedExpiry("测试当天到期", LocalDate.now().toString(), 0));
        ExpiryItem ahead = repository.add(new ParsedExpiry("测试提前7天", LocalDate.now().plusDays(7).toString(), 7));
        createdIds.add(today.getId());
        createdIds.add(ahead.getId());
        getTargetContext().sendBroadcast(new Intent(getTargetContext(), ReminderReceiver.class));
        long end = SystemClock.uptimeMillis() + 8000;
        boolean foundToday = false, foundAhead = false;
        while (SystemClock.uptimeMillis() < end && !(foundToday && foundAhead)) {
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                String title = notification.getNotification().extras.getString("android.title", "");
                foundToday |= title.equals("测试当天到期 今天到期");
                foundAhead |= title.equals("测试提前7天 快到期");
            }
            SystemClock.sleep(150);
        }
        check(foundToday, "生产提醒Receiver识别当天到期并发出通知（测试主动触发）");
        check(foundAhead, "生产提醒Receiver识别提前7天并发出通知（测试主动触发）");
        manager.cancel(today.getId().hashCode());
        manager.cancel(ahead.getId().hashCode());
    }

    private void testScheduledReminderSetup() {
        String title = "测试后台定时通知";
        repository.getAll().stream().filter(i -> i.getTitle().equals(title))
            .forEach(i -> repository.delete(i.getId()));
        repository.add(new ParsedExpiry(title, LocalDate.now().toString(), 0));
        PendingIntent operation = scheduledTestIntent();
        getTargetContext().getSystemService(AlarmManager.class).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60_000,
            operation
        );
        pass("已登记60秒后的后台定时通知测试");
    }

    private void testScheduledReminderVerify() {
        String title = "测试后台定时通知";
        NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
        boolean found = false;
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            found |= (title + " 今天到期").equals(
                notification.getNotification().extras.getString("android.title", "")
            );
        }
        repository.getAll().stream().filter(i -> i.getTitle().equals(title))
            .forEach(i -> repository.delete(i.getId()));
        manager.cancel(title.hashCode());
        getTargetContext().getSystemService(AlarmManager.class).cancel(scheduledTestIntent());
        check(found, "APP不在前台且屏幕关闭时，系统定时唤醒并发出到期通知");
    }

    private PendingIntent scheduledTestIntent() {
        return PendingIntent.getBroadcast(
            getTargetContext(),
            20260909,
            new Intent(getTargetContext(), ReminderReceiver.class)
                .setAction("com.daoqiji.app.TEST_SCHEDULED_REMINDER"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void cleanupKnownTestItems() {
        for (ExpiryItem item : repository.getAll()) {
            boolean known = item.getTitle().equals("护照") && item.getExpireDate().equals("2029-05-10") &&
                item.getRemindBeforeDays() == 90 ||
                item.getTitle().equals("健身会员") && item.getExpireDate().equals("2026-10-22") &&
                    item.getRemindBeforeDays() == 7 ||
                item.getTitle().equals("酸奶") && item.getExpireDate().equals("2026-09-12") ||
                item.getTitle().equals("测试当天到期") && item.getExpireDate().equals("2026-09-07") ||
                item.getTitle().equals("测试提前7天") && item.getExpireDate().equals("2026-09-14");
            if (known) repository.delete(item.getId());
        }
        SystemClock.sleep(1200);
        check(repository.getAll().stream().noneMatch(item ->
            item.getTitle().equals("测试当天到期") || item.getTitle().equals("测试提前7天") ||
            item.getTitle().equals("健身会员") || item.getTitle().equals("酸奶") ||
            item.getTitle().equals("护照") && item.getExpireDate().equals("2029-05-10")),
            "本次自动化测试记录已清理");
    }

    private void enter(String text) {
        field(0, text);
        await(n -> "已识别".contentEquals(value(n.getText())));
    }

    private void addCurrent(String title, int days) {
        List<ExpiryItem> before = repository.getAll();
        click("确认添加");
        SystemClock.sleep(250);
        ExpiryItem added = repository.getAll().stream().filter(i -> before.stream().noneMatch(b ->
            b.getId().equals(i.getId()))).findFirst().orElseThrow(() -> new AssertionError("记录未保存"));
        createdIds.add(added.getId());
        check(added.getTitle().equals(title) && added.getRemindBeforeDays() == days, "中文事项新增成功：" + title);
    }

    private void field(int index, String text) {
        List<AccessibilityNodeInfo> fields = new ArrayList<>();
        collect(getUiAutomation().getRootInActiveWindow(), n ->
            "android.widget.EditText".contentEquals(value(n.getClassName())), fields);
        if (fields.size() <= index) throw new AssertionError("未找到输入框 " + index);
        Bundle value = new Bundle();
        value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        check(fields.get(index).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value), "输入文字：" + text);
        SystemClock.sleep(250);
    }

    private void click(String label) {
        scrollToTop();
        for (int attempt = 0; attempt < 8; attempt++) {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), n ->
                label.contentEquals(value(n.getText())) || label.contentEquals(value(n.getContentDescription())));
            if (node != null) {
                while (node != null && !node.isClickable()) node = node.getParent();
                if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    SystemClock.sleep(300);
                    return;
                }
            }
            AccessibilityNodeInfo scroll = find(getUiAutomation().getRootInActiveWindow(), AccessibilityNodeInfo::isScrollable);
            if (scroll != null) scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(300);
        }
        throw new AssertionError("无法点击：" + label);
    }

    private AccessibilityNodeInfo await(Predicate<AccessibilityNodeInfo> predicate) {
        long end = SystemClock.uptimeMillis() + 8000;
        do {
            AccessibilityNodeInfo found = find(getUiAutomation().getRootInActiveWindow(), predicate);
            if (found != null) return found;
            SystemClock.sleep(150);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("等待界面超时");
    }

    private AccessibilityNodeInfo awaitWithScroll(Predicate<AccessibilityNodeInfo> predicate) {
        long end = SystemClock.uptimeMillis() + 8000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo found = find(root, predicate);
            if (found != null) return found;
            AccessibilityNodeInfo scroll = find(root, AccessibilityNodeInfo::isScrollable);
            if (scroll != null) scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(300);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("滚动查找界面超时");
    }

    private void scrollToTop() {
        for (int attempt = 0; attempt < 8; attempt++) {
            AccessibilityNodeInfo scroll = find(
                getUiAutomation().getRootInActiveWindow(), AccessibilityNodeInfo::isScrollable
            );
            if (scroll == null || !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return;
            SystemClock.sleep(150);
        }
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> predicate) {
        if (node == null) return null;
        if (predicate.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), predicate);
            if (found != null) return found;
        }
        return null;
    }

    private void collect(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> predicate,
                         List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        if (predicate.test(node)) result.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), predicate, result);
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(450);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("截图失败");
        File file = new File(getTargetContext().getExternalFilesDir("screenshots"), name + ".png");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
        pass("真机截图：" + file.getName());
    }

    private static String value(CharSequence value) { return value == null ? "" : value.toString(); }
    private static String chinese(LocalDate date) {
        return date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }
    private void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
        pass(detail);
    }
    private void pass(String detail) { evidence.add("PASS " + detail); }
}
