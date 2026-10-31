package local.mitv.launcher;

public final class ArticleHtmlTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Article assertion " + assertions); }
    public static void main(String[] args) throws Exception {
        String text = "This is the selected article body, with enough original text to distinguish it from navigation and placeholders. ";
        String html = "<html><div class='content'>NAV_OUTSIDE<div class=\"Mid2L_con wide\"><h1>Duplicate heading</h1><p>" + text
            + "</p><div><p>nested paragraph</p></div><script>var fake='<div>not markup</div>';</script>"
            + "<div>last paragraph</div></div><div>SIDEBAR_OUTSIDE</div></div></html>";
        String body = ArticleHtml.extract(html,"www.gamersky.com");
        check(body != null && body.contains(text)); check(body.contains("nested paragraph") && body.contains("last paragraph"));
        check(!body.contains("NAV_OUTSIDE") && !body.contains("SIDEBAR_OUTSIDE"));
        check(!body.contains("script") && !body.contains("Duplicate heading"));
        check(ArticleHtml.extract(html,"example.com") == null);
        check(ArticleHtml.extract("<div class='Mid2L_con'><p>short</p></div>","www.gamersky.com") == null);
        check(ArticleHtml.extract("<div id='content1'><p>" + text + "</p></div>","news.sciencenet.cn") != null);
        check(ArticleHtml.extract("<div id='other'><p>" + text + "</p></div>","news.sciencenet.cn") == null);
        check(ArticleHtml.extract("<div id='printContent'><div class='content'><p>"+text+"</p></div><div>SIDEBAR</div></div>","www.stdaily.com").equals("<p>"+text+"</p>"));
        check(ArticleHtml.extract("<div class='content'><p>"+text+"</p></div>","www.stdaily.com.attacker.test")==null);
        check(ArticleHtml.clean("<p>body</p><iframe>hidden</iframe><form>form</form><meta http-equiv='refresh'><link href='external'><script>x</script>").equals("<p>body</p>"));
        check(ArticleHtml.extract("<div class='MidLcon'><p>" + text,"www.gamersky.com") == null);
        check(ArticleHtml.extract("<div class='MidLcon'><p>" + text + "</p></div>","www.gamersky.com") != null);
        check(ArticleHtml.clean("<p><a href='https://example.test/' tabindex='0'>阅读文字</a></p>").equals("<p><span>阅读文字</span></p>"));
        check(ArticleHtml.extract("<script>var contentdate = '<p>" + text + "</p>';</script>","news.cctv.com").equals("<p>"+text+"</p>"));
        check(ArticleHtml.extract("<script>var contentdate = \"<p>"+text+"\\\"quoted\\\"</p>\";</script>","news.cctv.com").contains("\"quoted\""));
        check(ArticleHtml.extract("<script>var contentdate = '<p>"+text+"\\u4e2d\\u6587\\x21</p>';</script>","news.cctv.com").contains("中文!"));
        check(ArticleHtml.extract("<script>var contentdate = '<p>"+text+"\\'quoted\\'</p>';</script>","news.cctv.com").contains("'quoted'"));
        check(ArticleHtml.extract("<script>var contentdate = '<p>unterminated", "news.cctv.com") == null);
        check(!ArticleHtml.clean("<p>[!--begin:htmlVideoCode--]private player id[!--end:htmlVideoCode--]</p>").contains("player id"));
        check(ArticleHtml.extract("<div id='maincontent'><p>" + text + "</p></div>", "www.theguardian.com") != null);
        check(ArticleHtml.extract("<div id='story_text'><p>" + text + "</p></div>", "www.sciencedaily.com") != null);
        check(ArticleHtml.extract("<div id='maincontent'><article><p>" + text + "</p></article></div>", "www.theguardian.com") != null);
        check(ArticleHtml.extract("<div id='maincontent'><p>short</p></div>", "www.theguardian.com") == null);
        check(ArticleHtml.extract("<div id='story_text'><p>" + text + "</p></div>", "www.sciencedaily.com.attacker.test") == null);
        System.out.println("PASS: " + assertions + " static article assertions");
        for (String file : args) {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)),java.nio.charset.StandardCharsets.UTF_8);
            String article = ArticleHtml.extract(source,file.contains("cctv") ? "news.cctv.com"
                : file.contains("science-article") ? "news.sciencenet.cn" : file.contains("daily-article") ? "www.stdaily.com" : "www.gamersky.com");
            check(article != null && article.length() > 500);
            System.out.println("ARTICLE FIXTURE: " + file + " body chars=" + article.length());
        }
    }
}
