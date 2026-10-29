package local.mitv.launcher;

/** Shared menu order and non-wrapping remote movement, independent of Android focus search. */
final class SettingsMenu {
    static final int COLUMNS = 4;
    static final String[] LABELS = {"电视设置", "首页配置", "网络与连接", "蓝牙", "图像", "声音",
        "文件管理", "无线投屏", "本地图片屏保", "桌面接管与恢复", "数据与开源说明", "关于"};
    static final String[] DETAILS = {"打开电视原生设置", "应用、信号源名称、外观与内容显示", "Wi-Fi 与网络连接",
        "连接遥控器、耳机与其他设备", "电视画面与显示设置", "电视声音与输出设置", "浏览电视存储与已连接设备",
        "打开电视投屏入口", "管理照片与闲置轮播", "管理 Home 接管与恢复", "查看数据来源与开源许可", "查看项目名、版本与源码地址"};
    private SettingsMenu() {}

    static int move(int index, int dx, int dy) {
        if (index < 0 || index >= LABELS.length) return 0;
        if (Math.abs(dx) + Math.abs(dy) != 1) return index;
        int row = index / COLUMNS + dy, column = index % COLUMNS + dx;
        int next = row * COLUMNS + column;
        return row >= 0 && column >= 0 && column < COLUMNS && next < LABELS.length ? next : index;
    }
}
