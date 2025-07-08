/**
 *  Business Engine Extension
 */
/****************************************************************************************
 Extension Name: UpdAuthorizer
 Type : ExtendM3Transaction
 Script Author: Arun Gopal
 Date: 2025-05-06
 Description: Updating Authorizer MPHEAD (PPS200/F) via API transaction as the
              standard API transaction Update of PPS200MI does not have the provision
 Revision History:
 Name                 Date             Version          Description of Changes
 Arun Gopal           2025-05-06       1.0              Initial Version
 ******************************************************************************************/
/**
 * Parameters: (All parameters are NOT mandatory)
 * CONO - Company
 * FACI - Facility (Mandatory)
 * PUNO - Purchase order number (Mandatory)
 * AURE - Authorized (Mandatory)
 */

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UpdAuthorizer extends ExtendM3Transaction {
  private final MIAPI mi;
  private final DatabaseAPI database;
  private final ProgramAPI program;
  String AURE;

  public UpdAuthorizer(MIAPI mi, DatabaseAPI database, ProgramAPI program) {
    this.mi = mi;
    this.database = database;
    this.program = program;
  }

  /**
   * Main method
   * @return
   */
  public void main() {
    int CONO = mi.getIn().get("CONO") == null ? (int) program.getLDAZD().get("CONO") : (int) mi.getIn().get("CONO");
    String FACI = (String) mi.getIn().get("FACI");
    String PUNO = (String) mi.getIn().get("PUNO");
    AURE = (String) mi.getIn().get("AURE");
    String TX40 = "";
    // Validation for PO number
    DBAction dbaMPHEAD = database.table("MPHEAD")
      .index("20")
      .selection("IAORTY", "IAWHLO")
      .build();
    DBContainer conMPHEAD = dbaMPHEAD.getContainer();
    conMPHEAD.setInt("IACONO", CONO);
    conMPHEAD.set("IAFACI", FACI);
    conMPHEAD.set("IAPUNO", PUNO);
    if (!dbaMPHEAD.read(conMPHEAD)) {
      mi.error("Purchase order " + PUNO + " does not exist");
      return;
    }
    // Validation for Authorizer
    DBAction dbaCMNUSR = database.table("CMNUSR")
      .index("00")
      .selection("JUTX40")
      .build();
    DBContainer conCMNUSR = dbaCMNUSR.getContainer();
    conCMNUSR.setInt("JUCONO", 0);
    conCMNUSR.set("JUDIVI", "");
    conCMNUSR.set("JUUSID", AURE);
    if (!dbaCMNUSR.read(conCMNUSR)) {
      mi.error("Authorizer " + AURE + " does not exist");
      return;
    }
    // Validation for Authorizer in CUGEX3
    DBAction dbaCUGEX3 = database.table("CUGEX3").index("00").build();
    DBContainer conCUGEX3 = dbaCUGEX3.getContainer();
    conCUGEX3.setInt("F3CONO", CONO);
    conCUGEX3.set("F3KPID", "MPHEAD");
    conCUGEX3.set("F3PK01", conMPHEAD.getString("IAORTY"));
    conCUGEX3.set("F3PK02", AURE);
    conCUGEX3.set("F3PK03", FACI);
    conCUGEX3.set("F3PK04", conMPHEAD.getString("IAWHLO"));
    conCUGEX3.set("F3PK05", "");
    conCUGEX3.set("F3PK06", "");
    conCUGEX3.set("F3PK07", "");
    conCUGEX3.set("F3PK08", "");
    if (!dbaCUGEX3.read(conCUGEX3)) {
      mi.error("Authorizer " + AURE + " does not exist in CUGEX3");
      return;
    }
    TX40 = conCMNUSR.get("JUTX40");
    // Update Authorizer in PPS200/F panel
    DBAction queryToWrite = database.table("MPHEAD")
      .index("20")
      .selection("IAAURE")
      .build();
    DBContainer containerToWrite = queryToWrite.getContainer();
    containerToWrite.setInt("IACONO", CONO);
    containerToWrite.set("IAFACI", FACI);
    containerToWrite.set("IAPUNO", PUNO);
    queryToWrite.readLock(containerToWrite, updateCallBack);

    mi.outData.put("TX40", TX40);
    mi.write();
  }

  /**
   * Update Authorized in Purchase order header
   */
  Closure < ? > updateCallBack = {
    LockedResult lockedResult ->
    Integer LMDT = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) as Integer;
    Integer CHNO = (Integer) lockedResult.get("IACHNO") + 1;
    String CHID = program.getUser();
    lockedResult.set("IAAURE", AURE);
    lockedResult.set("IALMDT", LMDT);
    lockedResult.set("IACHNO", CHNO);
    lockedResult.set("IACHID", CHID);
    lockedResult.update();
  }
}