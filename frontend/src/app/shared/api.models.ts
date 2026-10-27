// Mirrors the backend DTOs (records under com.mamitiana.efacture).

export interface Company {
  legalName: string;
  siren: string;
  vatNumber: string;
  street: string;
  postalCode: string;
  city: string;
  email: string | null;
  iban: string | null;
  paymentTerms: string;
}

export interface Customer {
  id: number;
  legalName: string;
  siren: string | null;
  vatNumber: string | null;
  street: string;
  postalCode: string;
  city: string;
  email: string | null;
}

export type CustomerRequest = Omit<Customer, 'id'>;

export type InvoiceType = 'INVOICE' | 'CREDIT_NOTE';
export type InvoiceStatus = 'DRAFT' | 'ISSUED';

export interface InvoiceLine {
  position: number;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
  netAmount: number;
}

export interface VatBreakdown {
  rate: number;
  taxableAmount: number;
  vatAmount: number;
}

export interface Invoice {
  id: number;
  type: InvoiceType;
  status: InvoiceStatus;
  number: string | null;
  customerId: number;
  customerName: string;
  originalInvoiceNumber: string | null;
  issueDate: string | null;
  dueDate: string | null;
  currency: string;
  lines: InvoiceLine[];
  vatBreakdown: VatBreakdown[];
  totalNet: number;
  totalVat: number;
  totalGross: number;
  version: number;
}

export interface InvoiceSummary {
  id: number;
  type: InvoiceType;
  status: InvoiceStatus;
  number: string | null;
  customerName: string;
  issueDate: string | null;
  dueDate: string | null;
  totalGross: number;
}

export interface InvoiceRequest {
  customerId: number;
  dueDate: string | null;
  lines: { description: string; quantity: number; unitPrice: number; vatRate: number }[];
}

export const VAT_RATES = [20, 10, 5.5, 2.1, 0] as const;
