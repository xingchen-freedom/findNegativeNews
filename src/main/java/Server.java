import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.commons.io.IOUtils;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class Server {

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final String baoBianApiUrl = "http://baobianapi.pullword.com:9091/get.php";

    public static void main(String[] args) {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(8800),0);
            server.createContext("/baidunews", new BaiduNewsHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static String sendPost(String url, String jsonStr) {
        String result =null;
        CloseableHttpResponse response = null;

        StringEntity stringEntity = new StringEntity(jsonStr, StandardCharsets.UTF_8);
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(stringEntity);

        try {
            response = httpClient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            HttpEntity entity = response.getEntity();
            if (entity!=null){
                result = EntityUtils.toString(entity);
            }
        }catch (ParseException | IOException e){
            e.printStackTrace();
        }finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static class BaiduNewsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            Document html = null;
            Map<String, String> news = new HashMap<>();
            try {
                html = Jsoup.connect("http://news.baidu.com/").get();
                Elements newsATags = html.select("#pane-news")
                        .select("ul")
                        .select("li")
                        .select("a");
                if (newsATags != null){
                    for (Element a : newsATags){
                        news.put(a.text(), a.attr("href"));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Gson gson = new Gson();
            StringBuilder sb = new StringBuilder();

            sb.append("<html>");
            sb.append("<head>");
            sb.append("<title>百度热门负面新闻</title>");
            sb.append("<meta charset=\"utf-8\" >");
            sb.append("</head>");
            sb.append("<body>");


            if (news.isEmpty()){
                sb.append("<h1>result is empty</h1>");
            }else {
                for (Map.Entry<String,String> entry : news.entrySet()){
                    String res = sendPost(baoBianApiUrl,entry.getKey());
                    Map<String,Double> jsonMap = gson.fromJson(res, HashMap.class);
                    if (jsonMap.get("result")<(-0.5)){
                        sb.append("<p><a href=\""+entry.getValue()+"\">"+entry.getKey()+"</a></p>");
                    };
                }
            }

            sb.append("</body>");
            sb.append("</html>");

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }
}
