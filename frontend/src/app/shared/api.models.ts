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

export type PaymentMeans = 'TRANSFER' | 'DIRECT_DEBIT' | 'CARD' | 'CHEQUE' | 'CASH' | 'ALREADY_PAID' | 'UNKNOWN';

export const PAYMENT_MEANS_LABELS: Record<PaymentMeans, string> = {
  TRANSFER: 'Virement',
  DIRECT_DEBIT: 'Prélèvement',
  CARD: 'Carte bancaire',
  CHEQUE: 'Chèque',
  CASH: 'Espèces',
  ALREADY_PAID: 'Déjà payée',
  UNKNOWN: 'Non précisé',
};

export interface ExtractedInvoice {
  supplierName: string | null;
  supplierSiren: string | null;
  supplierVatNumber: string | null;
  supplierIban: string | null;
  paymentMeans: PaymentMeans;
  invoiceNumber: string | null;
  issueDate: string | null;
  dueDate: string | null;
  currency: string | null;
  lines: {
    description: string | null;
    quantity: number | null;
    unitPrice: number | null;
    vatRate: number | null;
    netAmount: number | null;
  }[];
  totalNet: number | null;
  totalVat: number | null;
  totalGross: number | null;
}

export type SupplierInvoiceStatus = 'NEEDS_REVIEW' | 'CHECKED' | 'VALIDATED' | 'REJECTED';
export type SupplierInvoiceSource = 'FACTURX' | 'AI' | 'MANUAL';

export interface InvoiceIssue {
  code: string;
  severity: 'ERROR' | 'WARNING';
  message: string;
}

export interface SupplierInvoice {
  id: number;
  status: SupplierInvoiceStatus;
  source: SupplierInvoiceSource;
  fileName: string;
  data: ExtractedInvoice;
  issues: InvoiceIssue[];
  extraction: { provider: string; model: string; durationMs: number } | null;
  receivedBy: string;
  receivedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

export interface SupplierInvoiceSummary {
  id: number;
  status: SupplierInvoiceStatus;
  source: SupplierInvoiceSource;
  supplierName: string | null;
  invoiceNumber: string | null;
  issueDate: string | null;
  dueDate: string | null;
  totalGross: number | null;
  errorCount: number;
  receivedAt: string;
}

export const SUPPLIER_STATUS_LABELS: Record<SupplierInvoiceStatus, string> = {
  NEEDS_REVIEW: 'À vérifier',
  CHECKED: 'Contrôlée',
  VALIDATED: 'Validée',
  REJECTED: 'Rejetée',
};

export const SOURCE_LABELS: Record<SupplierInvoiceSource, string> = {
  FACTURX: 'Factur-X',
  AI: 'Lue par IA',
  MANUAL: 'Saisie manuelle',
};

export interface Dashboard {
  year: number;
  revenueNet: number;
  collectedGross: number;
  receivable: { count: number; totalGross: number };
  overdue: { count: number; totalGross: number };
  monthlyRevenue: { month: number; revenueNet: number }[];
  overdueInvoices: {
    id: number;
    number: string;
    customerName: string;
    dueDate: string;
    daysLate: number;
    totalGross: number;
  }[];
  actions: {
    type: 'REFUSED_WITHOUT_CREDIT_NOTE' | 'REJECTED_SUBMISSION' | 'DELIVERY_FAILED';
    invoiceId: number;
    number: string;
    customerName: string;
    reason: string | null;
  }[];
  draftCount: number;
}

export type PlatformStatus =
  | 'EN_COURS_ENVOI'
  | 'ECHEC_ENVOI'
  | 'DEPOSEE'
  | 'REJETEE'
  | 'MISE_A_DISPOSITION'
  | 'APPROUVEE'
  | 'REFUSEE'
  | 'ENCAISSEE';

export const PLATFORM_STATUS_LABELS: Record<PlatformStatus, string> = {
  EN_COURS_ENVOI: 'En cours d’envoi',
  ECHEC_ENVOI: 'Échec d’envoi',
  DEPOSEE: 'Déposée',
  REJETEE: 'Rejetée',
  MISE_A_DISPOSITION: 'Mise à disposition',
  APPROUVEE: 'Approuvée',
  REFUSEE: 'Refusée',
  ENCAISSEE: 'Encaissée',
};

export interface SubmissionEvent {
  status: PlatformStatus;
  reason: string | null;
  source: 'SELLER' | 'PLATFORM';
  occurredAt: string;
}

export interface Submission {
  externalId: string;
  status: PlatformStatus;
  statusReason: string | null;
  finalStatus: boolean;
  submittedAt: string;
  updatedAt: string;
  deliveryAttempts: number;
  lastDeliveryError: string | null;
  events: SubmissionEvent[];
  simulated: boolean;
}
