package local.mitv.launcher;

public final class SaverPolicyTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Saver assertion " + assertions); }
    public static void main(String[] args) {
        check(SaverPolicy.waitMinutes(0) == 12);
        check(SaverPolicy.waitMinutes(61) == 12);
        check(SaverPolicy.waitMinutes(1) == 1);
        check(SaverPolicy.waitMinutes(60) == 60);
        check(SaverPolicy.slideSeconds(0) == 30);
        check(SaverPolicy.slideSeconds(9) == 30);
        check(SaverPolicy.slideSeconds(301) == 30);
        check(SaverPolicy.slideSeconds(10) == 10);
        check(SaverPolicy.imageName("PHOTO.JPG"));
        check(SaverPolicy.imageName("photo.jpeg"));
        check(SaverPolicy.imageName("photo.webp"));
        check(!SaverPolicy.imageName("photo.jpg.exe"));
        check(!SaverPolicy.imageName("notes.txt"));
        System.out.println("PASS: " + assertions + " screensaver policy assertions");
    }
}
