package local.mitv.launcher;

public final class SettingsMenuTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if(!value)throw new AssertionError("Settings assertion "+assertions); }
    public static void main(String[] args) {
        check(SettingsMenu.COLUMNS==4 && SettingsMenu.LABELS.length==12 && SettingsMenu.DETAILS.length==12);
        check(SettingsMenu.LABELS[0].equals("电视设置") && SettingsMenu.LABELS[11].equals("关于"));
        check(SettingsMenu.LABELS[8].equals("本地图片屏保") && SettingsMenu.LABELS[9].equals("桌面接管与恢复"));
        check(SettingsMenu.move(0,-1,0)==0 && SettingsMenu.move(0,0,-1)==0);
        check(SettingsMenu.move(3,1,0)==3 && SettingsMenu.move(8,-1,0)==8);
        check(SettingsMenu.move(11,1,0)==11 && SettingsMenu.move(11,0,1)==11);
        int index=0;
        index=SettingsMenu.move(index,1,0);check(SettingsMenu.LABELS[index].equals("配置首页应用"));
        index=SettingsMenu.move(index,0,1);check(SettingsMenu.LABELS[index].equals("声音"));
        index=SettingsMenu.move(index,0,1);check(SettingsMenu.LABELS[index].equals("桌面接管与恢复"));
        index=SettingsMenu.move(index,1,0);check(SettingsMenu.LABELS[index].equals("数据与开源说明"));
        index=SettingsMenu.move(index,1,0);check(index==11);
        index=SettingsMenu.move(index,0,-1);check(SettingsMenu.LABELS[index].equals("无线投屏"));
        index=SettingsMenu.move(index,0,-1);check(SettingsMenu.LABELS[index].equals("蓝牙"));
        check(SettingsMenu.move(-1,1,0)==0 && SettingsMenu.move(12,0,-1)==0);
        check(SettingsMenu.move(5,1,1)==5 && SettingsMenu.move(5,0,0)==5);
        System.out.println("PASS: "+assertions+" settings grid navigation assertions");
    }
}
