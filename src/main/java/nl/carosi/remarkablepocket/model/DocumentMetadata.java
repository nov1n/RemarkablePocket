package nl.carosi.remarkablepocket.model;

import es.jlarriba.jrmapi.model.Document;

public final record DocumentMetadata(Document doc, int pageCount, String pocketId) {}
