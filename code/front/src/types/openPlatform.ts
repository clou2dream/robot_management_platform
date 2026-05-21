export interface OpenPlatformCredentialStatus {
  bound: boolean;
  count: number;
}

export interface OpenPlatformCredential {
  id: string;
  displayName?: string;
  appId: string;
  apiKeyMasked: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SaveOpenPlatformCredentialRequest {
  displayName?: string;
  appId: string;
  apiKey: string;
  apiSecret: string;
}
