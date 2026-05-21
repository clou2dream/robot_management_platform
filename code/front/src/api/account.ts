import { http } from "./http";
import type {
  OpenPlatformCredential,
  OpenPlatformCredentialStatus,
  SaveOpenPlatformCredentialRequest
} from "../types/openPlatform";

export const getOpenPlatformCredentialStatus = async () => {
  const response = await http.get<OpenPlatformCredentialStatus>("/account/open-platform-credentials/status");
  return response.data;
};

export const getOpenPlatformCredentials = async () => {
  const response = await http.get<OpenPlatformCredential[]>("/account/open-platform-credentials");
  return response.data;
};

export const createOpenPlatformCredential = async (payload: SaveOpenPlatformCredentialRequest) => {
  const response = await http.post<OpenPlatformCredential>("/account/open-platform-credentials", payload);
  return response.data;
};

export const updateOpenPlatformCredential = async (
  credentialId: string,
  payload: SaveOpenPlatformCredentialRequest
) => {
  const response = await http.put<OpenPlatformCredential>(
    `/account/open-platform-credentials/${credentialId}`,
    payload
  );
  return response.data;
};

export const deleteOpenPlatformCredential = async (credentialId: string) => {
  await http.delete(`/account/open-platform-credentials/${credentialId}`);
};
