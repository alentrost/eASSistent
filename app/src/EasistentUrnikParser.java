import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.io.IOException;

public class EasistentUrnikParser {
    public static void main(String[] args) {
        try {
            // Prebere lokalno HTML datoteko
            File input = new File("batoteka.txt");
            Document doc = Jsoup.parse(input, "UTF-8", "");

            // Izbere glavno tabelo urnika
            Elements rows = doc.select("table.ednevnik-seznam_ur_teden tbody tr");

            // Izpis glave tabele
            System.out.printf("%-10s | %-10s | %-10s | %-10s | %-10s | %-10s%n",
                    "Ura", "Ponedeljek", "Torek", "Sreda", "Cetrtek", "Petek");
            System.out.println("-".repeat(77));

            // Zanka gre skozi vsako vrstico (vsako uro)
            for (Element row : rows) {
                // Ime ure (npr. "1. ura")
                String imeUre = row.select("th .naziv-ure").text();
                
                if (imeUre.isEmpty()) continue;

                // Celice za posamezne dni
                Elements days = row.select("td.ednevnik-seznam_ur_teden-td");
                
                String[] predmeti = new String[5];
                for (int i = 0; i < days.size() && i < 5; i++) {
                    // Iščemo kratico predmeta
                    Element predmetElement = days.get(i).selectFirst(".ednevnik-title span");
                    predmeti[i] = (predmetElement != null) ? predmetElement.text() : "/";
                }

                // Izpis vrstice za trenutno uro
                System.out.printf("%-10s | %-10s | %-10s | %-10s | %-10s | %-10s%n",
                        imeUre, predmeti[0], predmeti[1], predmeti[2], predmeti[3], predmeti[4]);
            }

        } catch (IOException e) {
            System.err.println("Datoteke 'batoteka.txt' ni mogoce najti ali prebrati: " + e.getMessage());
        }
    }
}