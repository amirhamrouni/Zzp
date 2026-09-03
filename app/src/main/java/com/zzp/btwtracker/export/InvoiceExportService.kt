package com.zzp.btwtracker.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.zzp.btwtracker.data.CompanyProfileEntity
import com.zzp.btwtracker.data.InvoiceEntity
import java.io.ByteArrayOutputStream
import java.time.LocalDate

object InvoiceExportService {
    fun pdf(invoice: InvoiceEntity, company: CompanyProfileEntity): ByteArray {
        require(company.tradeName.isNotBlank()) { "Vul eerst het bedrijfsprofiel in" }
        val doc=PdfDocument(); val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,1).create()); val c=page.canvas
        val title=Paint().apply{textSize=24f;isFakeBoldText=true}; val head=Paint().apply{textSize=12f;isFakeBoldText=true}; val body=Paint().apply{textSize=11f}
        c.drawText("FACTUUR",40f,55f,title); c.drawText(company.tradeName,350f,55f,head)
        var y=78f; listOf(company.ownerName,company.address,"${company.postalCode} ${company.city}","KvK ${company.kvkNumber}","BTW-id ${company.vatId}",company.iban,company.email).filter{it.isNotBlank()}.forEach{c.drawText(it,350f,y,body);y+=16f}
        y=150f; c.drawText("Factuurnummer",40f,y,head);c.drawText(invoice.invoiceNumber,155f,y,body);y+=18f
        c.drawText("Factuurdatum",40f,y,head);c.drawText(LocalDate.ofEpochDay(invoice.issueDateEpochDay).toString(),155f,y,body);y+=18f
        c.drawText("Vervaldatum",40f,y,head);c.drawText(LocalDate.ofEpochDay(invoice.dueDateEpochDay).toString(),155f,y,body);y+=40f
        c.drawText("Factuur aan",40f,y,head);y+=18f;c.drawText(invoice.customerName,40f,y,body); invoice.customerEmail?.let{y+=16f;c.drawText(it,40f,y,body)};y+=40f
        c.drawText("Omschrijving",40f,y,head);c.drawText("Excl. btw",350f,y,head);c.drawText("BTW",440f,y,head);c.drawText("Totaal",500f,y,head);y+=22f
        c.drawText(invoice.description.take(45),40f,y,body);c.drawText(euro(invoice.netCents),350f,y,body);c.drawText("${invoice.vatRate}%",440f,y,body);c.drawText(euro(invoice.grossCents),500f,y,body);y+=35f
        c.drawLine(40f,y,555f,y,body);y+=25f;c.drawText("BTW-bedrag",350f,y,head);c.drawText(euro(invoice.vatCents),500f,y,body);y+=24f;c.drawText("Te betalen",350f,y,head);c.drawText(euro(invoice.grossCents),500f,y,head);y+=55f
        c.drawText("Betaal uiterlijk binnen ${company.paymentTermDays} dagen o.v.v. ${invoice.invoiceNumber}.",40f,y,body)
        doc.finishPage(page);val out=ByteArrayOutputStream();doc.writeTo(out);doc.close();return out.toByteArray()
    }
    private fun euro(cents:Long)="€ %.2f".format(cents/100.0).replace('.',',')
}
