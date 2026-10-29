package local.mitv.launcher;

public final class SettingsMenuTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if(!value)throw new AssertionError("Settings assertion "+assertions); }
    public static void main(String[] args) {
        check(SettingsMenu.COLUMNS==4 && SettingsMenu.COUNT==12);
        check(SettingsMenu.move(0,-1,0)==0 && SettingsMenu.move(0,0,-1)==0);
        check(SettingsMenu.move(3,1,0)==3 && SettingsMenu.move(8,-1,0)==8);
        check(SettingsMenu.move(11,1,0)==11 && SettingsMenu.move(11,0,1)==11);
        int index=0;
        index=SettingsMenu.move(index,1,0);check(index==1);
        index=SettingsMenu.move(index,0,1);check(index==5);
        index=SettingsMenu.move(index,0,1);check(index==9);
        index=SettingsMenu.move(index,1,0);check(index==10);
        index=SettingsMenu.move(index,1,0);check(index==11);
        index=SettingsMenu.move(index,0,-1);check(index==7);
        index=SettingsMenu.move(index,0,-1);check(index==3);
        check(SettingsMenu.move(-1,1,0)==0 && SettingsMenu.move(12,0,-1)==0);
        check(SettingsMenu.move(5,1,1)==5 && SettingsMenu.move(5,0,0)==5);
        System.out.println("PASS: "+assertions+" settings grid navigation assertions");
    }
}
