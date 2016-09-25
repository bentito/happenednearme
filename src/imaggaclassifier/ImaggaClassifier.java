package imaggaclassifier;

import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.bg.CircleBackground;
import com.kennycason.kumo.font.scale.SqrtFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.palette.ColorPalette;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.json.JSONArray;
import twitter4j.*;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.List;


/**
 * Created by btofel on 9/24/16 for HackGT
 */
public class ImaggaClassifier {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/requests", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange he) throws IOException {
            System.out.println("Serving the request");
            try {
                HashMap rh = new HashMap();

                Headers eh = he.getRequestHeaders();
                for (Map.Entry<String, List<String>> e : eh.entrySet()) {
                    rh.put(e.getKey(), e.getValue().get(0));
                }
                fakeInsta(Float.parseFloat((String) rh.get("Latitude")), Float.parseFloat((String) rh.get("Longitude")));
            } catch (Exception e) {
                System.out.println("ERROR: " + e);
                e.printStackTrace();
            }
        }
    }

    static void fakeInsta(Float lat, Float lon) throws InterruptedException {
        ArrayList twitList = new ArrayList();
        // The factory instance is re-useable and thread safe.
        Twitter twitter = TwitterFactory.getSingleton();
        String qryStr = String.format("geocode:%f,%f,1km filter:twimg", lat, lon);
        //"geocode:-22.912214,-43.230182,1km filter:twimg"
        Query query = new Query(qryStr);
        QueryResult result = null;
        try {
            result = twitter.search(query);
        } catch (TwitterException e) {
            e.printStackTrace();
        }
        for (Status status : result.getTweets()) {
            System.out.println("" + status.getMediaEntities()[0].getMediaURL());
            twitList.add(status.getMediaEntities()[0].getMediaURL());
        }
        HashMap myMap = instaClassify(twitList);
        System.out.println(new PrettyPrintingMap<String, String>(myMap));
        hashToTextToImg(myMap); // save wordFreqs.txt
    }

    static HashMap instaClassify(ArrayList instaUrls) throws InterruptedException {
        ArrayList classfierJSONs = new ArrayList();
        String apiKey = "acc_db2e8c1135859a7", apiSecret = "0edb16800ccf9aaf1f6c14c35993eb70";
        HashMap classMap = new HashMap();
        try {
            Iterator itr = instaUrls.iterator();
            while (itr.hasNext()) {
                HttpResponse response = Unirest.get("https://api.imagga.com/v1/tagging")
                        .queryString("url", itr.next())
                        .basicAuth(apiKey, apiSecret)
                        .header("Accept", "application/json")
                        .asJson();
                HttpResponse<JsonNode> jsonResponse = response;
                JSONArray ary = jsonResponse.getBody().getObject().getJSONArray("results");
                JSONObject obj1 = (JSONObject) ary.get(0); //hackey hack hackathon
                JSONArray ary2 = (JSONArray) obj1.get("tags");
                classfierJSONs.add(ary2);
                Thread.sleep(900);
            }
            classMap = jsonsToMap(classfierJSONs);
        } catch (UnirestException e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
        return classMap;
    }

    static HashMap jsonsToMap(ArrayList classConfidenceJSONArrays) {
        HashMap<String, Integer> ret = new HashMap();
        Iterator itr = classConfidenceJSONArrays.iterator();
        int prev = 0;
        while (itr.hasNext()) {
            JSONArray classConfidenceJSONArray = (JSONArray) itr.next();
            Iterator iitr = classConfidenceJSONArray.iterator(); // there's an annoying inner arraylist to deal with
            while (iitr.hasNext()) {
                JSONObject confTag = (JSONObject) iitr.next();
                String tag = (String) confTag.get("tag");
                float confidence = Float.parseFloat(confTag.get("confidence").toString());
                Integer conf = Math.round(confidence);
                if (!ret.containsKey(tag)) {
                    ret.put(tag, conf);
                } else {
                    prev = (int) ret.get(tag);
                    ret.put(tag, conf + prev);
                }
            }

        }
        return ret;
    }

    public static void hashToTextToImg(Map<String, Integer> data) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("wordFreqs.txt", "UTF-8");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
        Iterator it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String word = pair.getKey() + " ";
            Integer frequency = (Integer) pair.getValue();
            for (int i = 0; i < frequency; i++) {
                writer.println(word);
            }
        }
        writer.close();

        final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
        File file = new File("wordFreqs.txt");
        try {
            FileInputStream fis = new FileInputStream(file);
            final List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(fis);
            final Dimension dimension = new Dimension(600, 600);
            final WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
//            wordCloud.setPadding(2);
//            wordCloud.setBackground(new CircleBackground(300));
//            wordCloud.setColorPalette(new ColorPalette(new Color(0x4055F1), new Color(0x408DF1), new Color(0x40AAF1), new Color(0x40C5F1), new Color(0x40D3F1), new Color(0xFFFFFF)));
//            wordCloud.setFontScalar(new SqrtFontScalar(10, 40));
            wordCloud.build(wordFrequencies);
            wordCloud.writeToFile("wordFreq.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
