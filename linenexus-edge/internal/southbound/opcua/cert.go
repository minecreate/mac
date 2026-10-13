package opcua

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"net/url"
	"os"
	"path/filepath"
	"time"
)

func ensureClientCert(dir string) (certPath, keyPath string, err error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", "", err
	}
	certPath = filepath.Join(dir, "client_cert.pem")
	keyPath = filepath.Join(dir, "client_key.pem")
	if fileExists(certPath) && fileExists(keyPath) && validRSAKey(keyPath) {
		return certPath, keyPath, nil
	}

	certPEM, keyPEM, err := generateClientCert()
	if err != nil {
		return "", "", err
	}
	if err := os.WriteFile(certPath, certPEM, 0o644); err != nil {
		return "", "", err
	}
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		return "", "", err
	}
	return certPath, keyPath, nil
}

func generateClientCert() (certPEM, keyPEM []byte, err error) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, nil, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return nil, nil, err
	}
	uri, _ := url.Parse("urn:linenexus:opcua-client")
	tpl := &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			Organization: []string{"LineNexus"},
			CommonName:   "LineNexus OPC UA Client",
		},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(10 * 365 * 24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment | x509.KeyUsageContentCommitment | x509.KeyUsageDataEncipherment,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth},
		BasicConstraintsValid: true,
		URIs:                  []*url.URL{uri},
		DNSNames:              []string{"localhost"},
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, &priv.PublicKey, priv)
	if err != nil {
		return nil, nil, fmt.Errorf("生成客户端证书: %w", err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}),
		pem.EncodeToMemory(&pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(priv)}),
		nil
}

func validRSAKey(path string) bool {
	raw, err := os.ReadFile(path)
	if err != nil {
		return false
	}
	block, _ := pem.Decode(raw)
	if block == nil || block.Type != "RSA PRIVATE KEY" {
		return false
	}
	_, err = x509.ParsePKCS1PrivateKey(block.Bytes)
	return err == nil
}

func fileExists(path string) bool {
	st, err := os.Stat(path)
	return err == nil && !st.IsDir()
}
