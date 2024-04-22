package org.ciisa.tpw;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class HTMLValidator {
    private final File directory;
    private final Map<String, Document> documents = new HashMap<>();
    private final Map<String, Integer> scores = new HashMap<>();
    private final Map<String, String> comments = new HashMap<>();
    private boolean isValidSyntax = false;

    public HTMLValidator(File directory) {
        this.directory = directory;
    }

    public Score validate() throws IOException {
        File[] htmlFiles = directory.listFiles((d, name) -> name.endsWith(".html"));
        if (htmlFiles == null || htmlFiles.length != 3) {
            comments.put("general", "No responde a la consigna de tener 3 archivos html");
            scores.put("general",0);
            return createZeroScore();
        }

        // Validate HTML syntax
        isValidSyntax = isValidSyntax(htmlFiles);

        // Load documents
        for (File htmlFile : htmlFiles) {
            Document document = Jsoup.parse(htmlFile, StandardCharsets.UTF_8.name());
            documents.put(htmlFile.getName(), document);
        }

        // Validate documents
        for (Map.Entry<String, Document> entry : documents.entrySet()) {
            String filename = entry.getKey();
            Document doc = entry.getValue();
            validateDocument(filename, doc);
        }

        // Compile scores
        return compileScores();
    }

    // Método nuevo que crea un Score con todos los puntajes en 0 y los comentarios establecidos
    private Score createZeroScore() {
        Score score = new Score();
        score.notaInicio = 0;
        score.notaForm = 0;
        score.notaImagenes = 0;
        score.notaHtml = 0; // Indica que no hay un HTML válido
        score.notaFinal = 0; // La calificación final también sería 0
        score.comentarios = getFormattedComments(); // Recogemos los comentarios, que ya tienen la nota general indicando el problema
        return score;
    }

    private boolean isValidSyntax(File[] htmlFiles) {
        for (File htmlFile : htmlFiles) {
            try {
                Jsoup.parse(htmlFile, StandardCharsets.UTF_8.name());
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private void validateDocument(String filename, Document doc) throws IOException {
        Elements links = doc.select("a[href]");
        boolean linksValid = links.stream().anyMatch(link -> documents.containsKey(link.attr("href")));

        if (isContactoPage(doc)) {
            scores.put("form", validateContactoPage(doc, linksValid));
        } else if (isGaleriaPage(doc)) {
            scores.put("galeria", validateGaleriaPage(doc));
        } else if (isInicioPage(doc)) {
            scores.put("inicio", validateInicioPage(doc));
        } else {
            comments.put(filename, "No se reconoce la pagina");
            scores.put(filename, 0);
        }
    }

    private boolean isInicioPage(Document doc) {
        boolean hasLongText = doc.select("body").text().length() > 100;
        boolean hasInicioOrHomeInTitle = doc.select("title").text().matches(".*\\b(Inicio|Home)\\b.*");

        return hasLongText || hasInicioOrHomeInTitle;
    }

    private boolean isGaleriaPage(Document doc) {
        boolean hasImages = doc.select("img").size() > 1;

        return hasImages;
    }

    private boolean isContactoPage(Document doc) {
        boolean hasForm = !doc.select("form").isEmpty();

        return hasForm;
    }

    private int validateInicioPage(Document doc) {
        // Verificamos la existencia del documento HTML
        boolean hasHtml = !doc.select("html").isEmpty();
        boolean hasIntro = doc.body().text().length() > 100;
        Elements paragraphs = doc.select("p");
        Elements list = doc.select("ul, ol");
        Elements menuLinks = doc.select("nav a[href]");
        boolean hasMenuRedirection = menuLinks.size() > 2; // asumiendo que la redirección implica al menos 3 enlaces
        // Puedes verificar la presencia de enlaces internos, pero no puedes verificar el estado de redirección con JSoup.
        boolean hasCss = !doc.select("link[rel=stylesheet], style").isEmpty(); // Simple check for CSS
        boolean isResponsive = !doc.select("meta[name=viewport]").isEmpty();  // Heuristic for responsivity check

        // No podemos verificar con JSoup si el CSS es mínimo o si la página es creativa y original.
        if (!hasHtml) {
            comments.put("inicio", "No presenta evidencia de la construcción de un documento HTML para la página de inicio.");
            return 0;
        } else if (paragraphs.isEmpty() && list.isEmpty()) {
            comments.put("inicio", "Se observa un intento básico de construir un documento HTML, sin etiquetas de párrafo y sin lista.");
            return 13;
        } else if (hasIntro && !hasMenuRedirection || doc.body().text().contains("Lorem ipsum")) {
            comments.put("inicio", "Incluye estructura HTML, pero con errores y con introducción de la empresa con menos de 100 caracteres, así como también lista como menú principal sin redirección.");
            return 16;
        } else if (hasIntro && hasMenuRedirection) {
            // En este punto suponemos que la estructura HTML es correcta porque hasHtml es verdadero y hay párrafos y listas, pero no podemos verificar la indentación o el uso mínimo de CSS
            comments.put("inicio", "Incluye estructura HTML con introducción de la empresa con 100 o más caracteres y menú principal con redirección.");
            return 24;
        } else if (/*hasIntro && hasMenuRedirection &&*/ hasCss /*&& indentación correcta*/) {
            // Agregue otra comprobación aquí si es posible verificar la indentación o el uso mínimo de CSS.
            return 26;
        }
        // Si no se cumple ningún criterio, se asume el nivel más bajo
        return 28;
    }

    private int validateGaleriaPage(Document doc) throws IOException {
        // Construir un mapa con los nombres de los archivos de imagen que se encuentran en el directorio y subcarpetas
        Map<String, Boolean> imageFilesMap = new HashMap<>();
        Files.walk(directory.toPath())
                .filter(p -> p.toFile().isFile())
                .filter(p -> p.toString().endsWith(".jpg") || p.toString().endsWith(".png") || p.toString().endsWith(".gif"))
                .forEach(p -> imageFilesMap.put(directory.toPath().relativize(p).toString().replace(File.separator, "/"), Boolean.FALSE));

        Elements images = doc.select("img");
        boolean hasImages = !images.isEmpty();

        // Verificar que cada imagen en el documento HTML coincida con un archivo en el directorio o en subcarpetas
        boolean hasErrors = images.stream().anyMatch(img -> {
            String src = img.attr("src");
            boolean srcIsEmpty = src.isEmpty();
            boolean altIsEmpty = img.attr("alt").isEmpty();
            boolean fileExists = imageFilesMap.containsKey(src);  // Verificar si el src coincide con el archivo de imagen

            // Marcar como encontrada si coincide con un archivo de imagen real
            if (fileExists) {
                imageFilesMap.put(src, Boolean.TRUE);
            }

            return srcIsEmpty || altIsEmpty || !fileExists;       // Cambiar a `||` para que sea un error si cualquiera se cumple
        });

        boolean allImagesFound = !hasErrors && imageFilesMap.values().stream().allMatch(Boolean::booleanValue);  // Verificar si todas las imágenes indicadas se encontraron

        long localImagesCount = images.stream().filter(img -> imageFilesMap.containsKey(img.attr("src"))).count();
        boolean allImagesLocal = localImagesCount == images.size();
        boolean hasAltText = images.stream().allMatch(img -> !img.attr("alt").isEmpty());

        if (!hasImages) {
            comments.put("galeria", "No presenta evidencia de imágenes dentro de la página web.");
            return 0;
        } else if (hasErrors || !allImagesFound) {
            comments.put("galeria", "Implementación de etiquetas de imagen con errores o imágenes que no coinciden con archivos del directorio o subcarpetas.");
            return 13;
        } else if (images.size() < 4) {
            comments.put("galeria", "Implementación de etiquetas de imagen sin alcanzar el mínimo de imágenes solicitadas.");
            return 16;
        } else if (!allImagesLocal) {
            comments.put("galeria", "Implementación de etiquetas de imagen que no hacen referencia a un archivo local.");
            return 21;
        } else if (!hasAltText) {
            comments.put("galeria", "Implementación correcta de etiquetas de imagen con referencia a un archivo local, pero sin rellenar el campo de texto alternativo.");
            return 24;
        } else {
            return 26;
        }
    }

    private int validateContactoPage(Document doc, boolean linksValid) {
        Elements forms = doc.select("form");
        Elements requiredFields = doc.select("input[required], select[required], textarea[required]");
        int numberOfRequiredFields = requiredFields.size();
        boolean hasBasicForm = !forms.isEmpty();

        // Aquí se verifica si tiene todas las validaciones necesarias,
        // si hasBasicForm es falso se asume que no hay formulario.
        boolean hasAllValidations = hasBasicForm && forms.select("input[type=email], input[type=number], input[type=date]").size() > 0;

        // Aquí se asumen que los campos descriptivos tienen la etiqueta "label" asociada.
        boolean hasDescriptiveFields = hasBasicForm && forms.select("label").size() >= numberOfRequiredFields;

        if (!hasBasicForm) {
            comments.put("form", "No presenta evidencia de la construcción de un formulario web.");
            return 0;
        } else if (!hasAllValidations) {
            if (numberOfRequiredFields < 3) {
                comments.put("form", "Formulario incluido con menos de 3 campos requeridos.");
                return 29;
            } else {
                comments.put("form", "Intento básico de construir un formulario web sin validaciones.");
                return 19;
            }
        } else {
            if (!hasDescriptiveFields) {
                comments.put("form", "Formulario completo con todas las validaciones, pero los campos no son descriptivos.");
                return 43;
            } else {
                return 48;
            }
        }
    }

    private Score compileScores() {
        Score score = new Score();
        score.notaInicio = scores.containsKey("inicio") ? scores.get("inicio") : 0;
        score.notaForm = scores.containsKey("form") ? scores.get("form") : 0;
        score.notaImagenes = scores.containsKey("galeria") ? scores.get("galeria") : 0;
        score.notaHtml = isValidSyntax ? 1 : 0;
        score.notaFinal = (score.notaInicio + score.notaForm + score.notaImagenes) * score.notaHtml;
        score.comentarios = getFormattedComments();
        return score;
    }

    private String getFormattedComments() {
        StringBuilder formattedComments = new StringBuilder();
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            formattedComments.append("Nota sección ").append(entry.getKey()).append(": ")
                    .append(scores.get(entry.getKey())).append(". ")
                    .append(entry.getValue());
        }
        return formattedComments.toString().trim();
    }
}
