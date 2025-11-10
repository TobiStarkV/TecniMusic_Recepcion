package com.example.tecnimusic_recepcion;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import javafx.scene.control.Alert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PdfGenerator {

    private final String localNombre;
    private final String localDireccion;
    private final String localTelefono;
    private final String pdfFooter;
    private static final Locale SPANISH_MEXICO_LOCALE = new Locale("es", "MX");

    public PdfGenerator() {
        DatabaseConfig dbConfig = new DatabaseConfig();
        this.localNombre = dbConfig.getLocalNombre();
        this.localDireccion = dbConfig.getLocalDireccion();
        this.localTelefono = dbConfig.getLocalTelefono();
        this.pdfFooter = dbConfig.getPdfFooter();
    }

    public String generatePdf(HojaServicioData data) throws IOException {
        String dest = crearRutaDestinoPdf(data.getNumeroOrden());
        if (dest == null) return null;

        PdfWriter writer = new PdfWriter(dest);
        PdfDocument pdf = new PdfDocument(writer);

        // El margen inferior debe ser suficiente para el pie de página
        Document document = new Document(pdf);
        document.setMargins(20, 20, 80, 20); // Aumentar el margen inferior

        // Añadir el manejador de eventos para el pie de página
        if (pdfFooter != null && !pdfFooter.trim().isEmpty()) {
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterEventHandler(pdfFooter, document));
        }

        com.itextpdf.kernel.colors.Color headerColor = new DeviceRgb(45, 65, 84);

        agregarEncabezado(document);
        agregarInformacionOrden(document, data);
        document.add(new LineSeparator(new SolidLine(1)).setMarginTop(5).setMarginBottom(5));
        agregarSeccionesDeDatos(document, data, headerColor);
        agregarSeccionFirma(document, data);
        agregarMarcaDeAgua(pdf);

        document.close();
        return dest;
    }

    // Clase interna para manejar el evento de pie de página
    protected static class FooterEventHandler implements IEventHandler {
        private final String footerText;
        private final Document doc;

        public FooterEventHandler(String footerText, Document doc) {
            this.footerText = footerText;
            this.doc = doc;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            Rectangle pageSize = page.getPageSize();

            // Define el área rectangular para el pie de página.
            float footerAreaHeight = 75; 
            float x = pageSize.getLeft() + doc.getLeftMargin();
            float y = pageSize.getBottom() + 35; // Subir más el pie de página
            float width = pageSize.getWidth() - doc.getLeftMargin() - doc.getRightMargin();
            Rectangle footerArea = new Rectangle(x, y, width, footerAreaHeight);

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), docEvent.getDocument());
            Canvas canvas = new Canvas(pdfCanvas, footerArea);

            Paragraph footer = new Paragraph(footerText)
                    .setFontSize(6) // Reducir más el tamaño de la fuente
                    .setItalic()
                    .setTextAlignment(TextAlignment.CENTER);

            canvas.add(footer);
            canvas.close();
        }
    }

    private String crearRutaDestinoPdf(String numeroOrden) {
        String destFolder = System.getProperty("user.home") + File.separator + "TecniMusic_Recepcion" + File.separator + "HojasDeServicio";
        File dir = new File(destFolder);
        if (!dir.exists() && !dir.mkdirs()) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Archivo", "No se pudo crear el directorio para guardar los PDFs.");
            return null;
        }
        return destFolder + File.separator + numeroOrden + ".pdf";
    }

    private void agregarEncabezado(Document document) throws IOException {
        URL logoUrl = getClass().getClassLoader().getResource("logo.png");
        if (logoUrl == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Logo no encontrado", "El archivo logo.png no se encontró en los recursos.");
            return;
        }

        Image logo = new Image(ImageDataFactory.create(logoUrl));
        logo.setHeight(40);

        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3})).useAllAvailableWidth();
        headerTable.addCell(new com.itextpdf.layout.element.Cell().add(logo).setBorder(Border.NO_BORDER));

        com.itextpdf.layout.element.Cell infoCell = new com.itextpdf.layout.element.Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        infoCell.add(new Paragraph(localNombre).setBold().setFontSize(12));
        infoCell.add(new Paragraph(localDireccion).setFontSize(8));
        infoCell.add(new Paragraph(localTelefono).setFontSize(8));
        headerTable.addCell(infoCell);

        document.add(headerTable);
    }

    private void agregarInformacionOrden(Document document, HojaServicioData data) {
        document.add(new Paragraph("Hoja de Servicio de Recepción").setTextAlignment(TextAlignment.CENTER).setFontSize(16).setBold().setMarginTop(10));
        Table orderInfoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(orderInfoTable, "No. de Orden:", data.getNumeroOrden(), true);
        LocalDate fechaOrden = data.getFechaOrden();
        addInfoRow(orderInfoTable, "Fecha de Recepción:", fechaOrden != null ? fechaOrden.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A", true);
        document.add(orderInfoTable);
    }

    private void agregarSeccionesDeDatos(Document document, HojaServicioData data, com.itextpdf.kernel.colors.Color headerColor) {
        document.add(createSectionHeader("Datos del Cliente", headerColor));
        Table clienteTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(clienteTable, "Nombre:", data.getClienteNombre(), false);
        addInfoRow(clienteTable, "Teléfono:", data.getClienteTelefono(), false);
        addInfoRow(clienteTable, "Dirección:", data.getClienteDireccion(), false);
        document.add(clienteTable);

        document.add(createSectionHeader("Equipos y Desglose de Costos", headerColor));

        List<Equipo> equipos = data.getEquipos();
        if (equipos != null && !equipos.isEmpty()) {
            Table equiposCostosTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.5f, 1.5f, 1.5f, 3, 1.5f})).useAllAvailableWidth().setMarginTop(5);

            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Tipo").setBold().setFontSize(9)).setBorder(Border.NO_BORDER));
            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Marca").setBold().setFontSize(9)).setBorder(Border.NO_BORDER));
            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Modelo").setBold().setFontSize(9)).setBorder(Border.NO_BORDER));
            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Serie").setBold().setFontSize(9)).setBorder(Border.NO_BORDER));
            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Falla Reportada").setBold().setFontSize(9)).setBorder(Border.NO_BORDER));
            equiposCostosTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Costo").setBold().setFontSize(9).setTextAlignment(TextAlignment.RIGHT)).setBorder(Border.NO_BORDER));

            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);

            for (Equipo eq : equipos) {
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullToEmpty(eq.getTipo())).setFontSize(8)).setBorder(Border.NO_BORDER));
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullToEmpty(eq.getMarca())).setFontSize(8)).setBorder(Border.NO_BORDER));
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullToEmpty(eq.getModelo())).setFontSize(8)).setBorder(Border.NO_BORDER));
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullToEmpty(eq.getSerie())).setFontSize(8)).setBorder(Border.NO_BORDER));
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nullToEmpty(eq.getFalla())).setFontSize(8)).setBorder(Border.NO_BORDER));
                String costoFormateado = eq.getCosto() != null ? currencyFormat.format(eq.getCosto()) : currencyFormat.format(0);
                equiposCostosTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(costoFormateado).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setBorder(Border.NO_BORDER));
            }
            document.add(equiposCostosTable);
        } else {
            document.add(new Paragraph("No se han registrado equipos.").setFontSize(9).setMarginTop(5));
        }

        document.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(5));

        Table totalesTable = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth().setMarginTop(5);
        totalesTable.setBorder(Border.NO_BORDER);

        BigDecimal subtotal = data.getTotalCostos() != null ? data.getTotalCostos() : BigDecimal.ZERO;
        BigDecimal anticipo = data.getAnticipo() != null ? data.getAnticipo() : BigDecimal.ZERO;
        BigDecimal totalFinal = subtotal.subtract(anticipo);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);

        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Subtotal:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold());
        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(subtotal))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));

        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Anticipo:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold());
        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(anticipo))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));

        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Total a Pagar:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold().setFontSize(12));
        totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(totalFinal))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold().setFontSize(12));

        document.add(totalesTable);

        document.add(createSectionHeader("Entrega y Cierre", headerColor));
        LocalDate fechaEntrega = data.getFechaEntrega();
        Table entregaTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(entregaTable, "Fecha de Entrega:", (fechaEntrega != null ? fechaEntrega.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Pendiente"), false);
        document.add(entregaTable);

        String aclaraciones = data.getAclaraciones();
        if (aclaraciones != null && !aclaraciones.trim().isEmpty()) {
            document.add(new Paragraph("Aclaraciones Adicionales:").setBold().setFontSize(9).setMarginTop(5));
            document.add(new Paragraph(aclaraciones).setFontSize(9));
        }
    }

    private void agregarSeccionFirma(Document document, HojaServicioData data) {
        document.add(new Paragraph("\n\n"));
        LineSeparator line = new LineSeparator(new SolidLine(1f));
        line.setWidth(UnitValue.createPercentValue(50));
        Paragraph centeredLineParagraph = new Paragraph().add(line);
        centeredLineParagraph.setTextAlignment(TextAlignment.CENTER);
        document.add(centeredLineParagraph);

        document.add(new Paragraph(data.getClienteNombre()).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
        document.add(new Paragraph("Firma de Conformidad").setTextAlignment(TextAlignment.CENTER).setFontSize(8).setItalic());
    }

    private void agregarMarcaDeAgua(PdfDocument pdf) throws IOException {
        URL logoUrl = getClass().getClassLoader().getResource("logo.png");
        if (logoUrl == null) return;

        com.itextpdf.io.image.ImageData imageData = ImageDataFactory.create(logoUrl);
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            Image watermarkImg = new Image(imageData).setOpacity(0.1f);
            PdfPage page = pdf.getPage(i);
            Rectangle pageSize = page.getPageSize();
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            watermarkImg.scaleToFit(pageSize.getWidth() * 0.9f, pageSize.getHeight() * 0.9f);
            float x = (pageSize.getWidth() - watermarkImg.getImageScaledWidth()) / 2;
            float y = (pageSize.getHeight() - watermarkImg.getImageScaledHeight()) / 2;
            try (Canvas canvas = new Canvas(pdfCanvas, pageSize)) {
                canvas.add(watermarkImg.setFixedPosition(x, y));
            }
        }
    }

    private Paragraph createSectionHeader(String title, com.itextpdf.kernel.colors.Color bgColor) {
        Paragraph p = new Paragraph(title);
        p.setBackgroundColor(bgColor);
        p.setFontColor(ColorConstants.WHITE);
        p.setBold();
        p.setPadding(3);
        p.setMarginTop(8);
        p.setFontSize(10);
        p.setTextAlignment(TextAlignment.CENTER);
        return p;
    }

    private void addInfoRow(Table table, String label, String value, boolean isValueBold) {
        if (value == null) value = "";
        com.itextpdf.layout.element.Cell labelCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(label).setBold().setFontSize(9));
        labelCell.setBorder(Border.NO_BORDER).setPadding(1);
        table.addCell(labelCell);

        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(value).setFontSize(9));
        if (isValueBold) {
            valueCell.setBold();
        }
        valueCell.setBorder(Border.NO_BORDER).setPadding(1);
        table.addCell(valueCell);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }
}
