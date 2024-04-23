package org.ciisa.tpw;

import java.io.FileWriter;
import java.io.IOException;

public class CSVWriter {
    private FileWriter writer;

    public CSVWriter(String outputPath) throws IOException {
        writer = new FileWriter(outputPath);
    }

    public void writeHeader() throws IOException {
        writer.write("Nombre,2do Nombre,Primer Apellido,2do apellido,nota inicio,nota imagenes,nota form,nota html,notal final,comentarios\n");
    }

    public void writeLine(String dirName, Score score) throws IOException {
        // Divide dirName by underscore '_'
        String[] parts = dirName.split("_");
        String firstPart = parts[0];  // Take the first part

        // Split the first part by spaces
        String[] nameComponents = firstPart.split(" ");

        // Determine the number of components and adjust the CSV fields according to the number of names
        String csvLine;
        if (nameComponents.length == 3) {
            // If there are only three names, keep the second CSV field empty
            csvLine = String.format("%s,%s,%s,%s,%d,%d,%d,%d,%d,%s\n",
                    nameComponents[0], "", nameComponents[1], nameComponents[2],
                    score.notaInicio, score.notaImagenes, score.notaForm, score.notaHtml, score.notaFinal, "\""+score.comentarios+ "\"");
        } else {
            // If there are four names, separate each into the CSV fields
            // Ensure that there are at least four components, fill the missing ones with an empty string if necessary
            String[] finalComponents = new String[4];
            for (int i = 0; i < finalComponents.length; i++) {
                finalComponents[i] = (i < nameComponents.length) ? nameComponents[i] : "";
            }
            csvLine = String.format("%s,%s,%s,%s,%d,%d,%d,%d,%d,%s\n",
                    finalComponents[0], finalComponents[1], finalComponents[2], finalComponents[3],
                    score.notaInicio, score.notaImagenes, score.notaForm, score.notaHtml, score.notaFinal, score.comentarios);
        }

        // Write the line to the CSV file
        writer.write(csvLine);
    }


    public void close() throws IOException {
        writer.close();
    }
}

