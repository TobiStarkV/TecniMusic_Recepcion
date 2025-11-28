package com.example.tecnimusic_recepcion;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
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
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PdfGenerator {

    private String localNombre;
    private String localDireccion;
    private String localTelefono;
    private String pdfFooter;
    private int pdfFooterFontSize;
    private static final Locale SPANISH_MEXICO_LOCALE = new Locale("es", "MX");

    public PdfGenerator() {
        try {
            DatabaseService db = DatabaseService.getInstance();
            this.localNombre = db.getSetting("local.nombre", "TecniMusic");
            this.localDireccion = db.getSetting("local.direccion", "Dirección no configurada");
            this.localTelefono = db.getSetting("local.telefono", "Teléfono no configurado");
            this.pdfFooter = db.getSetting("pdf.footer", "");
            this.pdfFooterFontSize = Integer.parseInt(db.getSetting("pdf.footer.fontsize", "6"));
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error de Configuración", "No se pudieron cargar las configuraciones desde la base de datos para generar el PDF.");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            this.pdfFooterFontSize = 6; // Valor por defecto si la configuración está corrupta
        }
    }

    public String generatePdf(HojaServicioData data, boolean forceReception) throws IOException {
        boolean isCierre = "CERRADA".equals(data.getEstado()) && !forceReception;
        String suffix = isCierre ? "_CIERRE" : "_RECEPCION";
        String dest = crearRutaDestinoPdf(data.getNumeroOrden() + suffix);
        if (dest == null) return null;

        PdfWriter writer = new PdfWriter(dest);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.setMargins(30, 30, 150, 30);

        com.itextpdf.kernel.colors.Color headerColor = new DeviceRgb(45, 65, 84);

        agregarEncabezado(document);
        agregarInformacionOrden(document, data, isCierre);
        document.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1)).setMarginTop(5).setMarginBottom(5));
        agregarSeccionesDeDatos(document, data, headerColor, isCierre);
        agregarMarcaDeAgua(pdf);
        agregarFirmaYPieDePagina(pdf, document, data.getClienteNombre(), isCierre);
        agregarNumerosDePagina(pdf, document);

        document.close();
        return dest;
    }

    public String generatePdf(HojaServicioData data) throws IOException {
        return generatePdf(data, false);
    }


    private void agregarNumerosDePagina(PdfDocument pdf, Document doc) {
        int totalPages = pdf.getNumberOfPages();
        if (totalPages <= 1) return;

        for (int i = 1; i <= totalPages; i++) {
            PdfPage page = pdf.getPage(i);
            Rectangle pageSize = page.getPageSize();
            new Canvas(new PdfCanvas(page), pageSize)
                .setFontSize(8)
                .showTextAligned("Página " + i + " de " + totalPages, pageSize.getWidth() / 2, pageSize.getTop() - 20, TextAlignment.CENTER)
                .close();
        }
    }

    private void agregarFirmaYPieDePagina(PdfDocument pdf, Document doc, String clienteNombre, boolean isCierre) {
        PdfPage lastPage = pdf.getLastPage();
        Rectangle pageSize = lastPage.getPageSize();
        PdfCanvas pdfCanvas = new PdfCanvas(lastPage);

        float margin = doc.getLeftMargin();
        float centerX = pageSize.getWidth() / 2;

        // La sección de la firma solo se añade si es un PDF de cierre.
        if (isCierre) {
            float signatureLineY = 100;
            float signatureLineWidth = 140;

            pdfCanvas.moveTo(centerX - (signatureLineWidth / 2), signatureLineY).lineTo(centerX + (signatureLineWidth / 2), signatureLineY).stroke();

            String firmaTitle = "Sello y Firma del Técnico";

            try (Canvas signatureTextCanvas = new Canvas(pdfCanvas, pageSize)) {
                // Se elimina el nombre del cliente y se coloca el nuevo título para el técnico.
                signatureTextCanvas
                    .showTextAligned(new Paragraph(firmaTitle).setFontSize(9).setBold(), centerX, signatureLineY - 15, TextAlignment.CENTER);
            }
        }

        // El pie de página con las aclaraciones se muestra en todos los PDFs.
        Rectangle footerTextRect = new Rectangle(margin, 30, pageSize.getWidth() - (margin * 2), 40);
        try (Canvas footerCanvas = new Canvas(pdfCanvas, footerTextRect)) {
            footerCanvas.add(new Paragraph(pdfFooter).setFontSize(pdfFooterFontSize).setItalic().setTextAlignment(TextAlignment.CENTER));
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

        Image logo = new Image(ImageDataFactory.create(logoUrl)).setHeight(40);
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3})).useAllAvailableWidth();
        headerTable.addCell(new com.itextpdf.layout.element.Cell().add(logo).setBorder(Border.NO_BORDER));

        com.itextpdf.layout.element.Cell infoCell = new com.itextpdf.layout.element.Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        infoCell.add(new Paragraph(localNombre).setBold().setFontSize(12));
        infoCell.add(new Paragraph(localDireccion).setFontSize(8));
        infoCell.add(new Paragraph(localTelefono).setFontSize(8));
        headerTable.addCell(infoCell);

        document.add(headerTable);
    }

    private void agregarInformacionOrden(Document document, HojaServicioData data, boolean isCierre) {
        String title = isCierre ? "Hoja de Cierre de Servicio" : "Hoja de Servicio de Recepción";
        document.add(new Paragraph(title).setTextAlignment(TextAlignment.CENTER).setFontSize(16).setBold().setMarginTop(10));
        Table orderInfoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(orderInfoTable, "No. de Orden:", data.getNumeroOrden(), true);
        LocalDate fechaOrden = data.getFechaOrden();
        addInfoRow(orderInfoTable, "Fecha de Recepción:", fechaOrden != null ? fechaOrden.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A", true);
        document.add(orderInfoTable);
    }

    private void agregarSeccionesDeDatos(Document document, HojaServicioData data, com.itextpdf.kernel.colors.Color headerColor, boolean isCierre) {
        document.add(createSectionHeader("Datos del Cliente", headerColor));
        Table clienteTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(5);
        addInfoRow(clienteTable, "Nombre:", data.getClienteNombre(), false);
        addInfoRow(clienteTable, "Teléfono:", data.getClienteTelefono(), false);
        addInfoRow(clienteTable, "Dirección:", data.getClienteDireccion(), false);
        document.add(clienteTable);

        document.add(createSectionHeader("Equipos y Desglose", headerColor));

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(SPANISH_MEXICO_LOCALE);
        List<Equipo> equipos = data.getEquipos();
        if (equipos != null && !equipos.isEmpty()) {
            for (Equipo eq : equipos) {
                Table equipoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2})).useAllAvailableWidth().setMarginTop(10);
                addInfoRow(equipoTable, "Tipo:", nullToEmpty(eq.getTipo()), false);
                addInfoRow(equipoTable, "Marca:", nullToEmpty(eq.getMarca()), false);
                addInfoRow(equipoTable, "Modelo:", nullToEmpty(eq.getModelo()), false);
                addInfoRow(equipoTable, "Serie:", nullToEmpty(eq.getSerie()), false);
                document.add(equipoTable);

                Table detallesTable = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth().setMarginTop(2);
                addInfoRow(detallesTable, "Estado Físico:", nullToEmpty(eq.getEstadoFisico()), false);
                addInfoRow(detallesTable, "Accesorios:", nullToEmpty(eq.getAccesorios()), false);
                addInfoRow(detallesTable, "Falla Reportada:", nullToEmpty(eq.getFalla()), false);
                if (isCierre) {
                    addInfoRow(detallesTable, "Informe Técnico:", nullToEmpty(eq.getInformeTecnico()), false);
                    BigDecimal costoEquipo = eq.getCosto() != null ? eq.getCosto() : BigDecimal.ZERO;
                    addInfoRow(detallesTable, "Costo de Reparación:", currencyFormat.format(costoEquipo), false);
                }
                document.add(detallesTable);
                document.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.DashedLine()).setMarginTop(5));
            }
        } else {
            document.add(new Paragraph("No se han registrado equipos.").setFontSize(9).setMarginTop(5));
        }

        document.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f)).setMarginTop(10));

        BigDecimal anticipo = data.getAnticipo() != null ? data.getAnticipo() : BigDecimal.ZERO;

        if (isCierre) {
            Table totalesTable = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth().setMarginTop(5);
            totalesTable.setBorder(Border.NO_BORDER);

            BigDecimal subtotal = data.getTotalCostos() != null ? data.getTotalCostos() : BigDecimal.ZERO;
            BigDecimal totalFinal = subtotal.subtract(anticipo);

            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Subtotal:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold());
            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(subtotal))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));

            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Anticipo:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold());
            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(anticipo))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));

            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Total a Pagar:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold().setFontSize(12));
            totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(totalFinal))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold().setFontSize(12));

            document.add(totalesTable);
        } else {
            if (anticipo.compareTo(BigDecimal.ZERO) > 0) {
                Table totalesTable = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth().setMarginTop(5);
                totalesTable.setBorder(Border.NO_BORDER);
                totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Anticipo:")).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setBold());
                totalesTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(currencyFormat.format(anticipo))).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));
                document.add(totalesTable);
            }
        }

        document.add(createSectionHeader("Entrega y Aclaraciones", headerColor));
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

    private void agregarMarcaDeAgua(PdfDocument pdf) throws IOException {
        URL logoUrl = getClass().getClassLoader().getResource("logo.png");
        if (logoUrl == null) return;

        com.itextpdf.io.image.ImageData imageData = ImageDataFactory.create(logoUrl);
        for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
            Image watermarkImg = new Image(imageData).setOpacity(0.2f); // Aumentado de 0.1f a 0.2f
            PdfPage page = pdf.getPage(i);
            Rectangle pageSize = page.getPageSize();
            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf);
            watermarkImg.scaleToFit(pageSize.getWidth() * 0.9f, pageSize.getHeight() * 0.9f);
            float x = (pageSize.getWidth() - watermarkImg.getImageScaledWidth()) / 2;
            float y = (pageSize.getHeight() - watermarkImg.getImageScaledHeight()) / 2;
            try (com.itextpdf.layout.Canvas canvas = new com.itextpdf.layout.Canvas(pdfCanvas, pageSize)) {
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

        Paragraph valueParagraph = new Paragraph(value).setFontSize(9);
        
        com.itextpdf.layout.element.Cell valueCell = new com.itextpdf.layout.element.Cell().add(valueParagraph);
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
