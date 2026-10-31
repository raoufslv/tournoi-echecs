import { copyText } from './copy-text';

describe('copyText', () => {
  it('uses execCommand when the clipboard API is absent', async () => {
    spyOnProperty(window, 'isSecureContext', 'get').and.returnValue(false);
    const exec = spyOn(document, 'execCommand').and.returnValue(true);

    await expectAsync(copyText('NEKHRF')).toBeResolvedTo(true);
    expect(exec).toHaveBeenCalledWith('copy');
  });

  it('returns false when the fallback cannot copy', async () => {
    spyOnProperty(window, 'isSecureContext', 'get').and.returnValue(false);
    spyOn(document, 'execCommand').and.returnValue(false);

    await expectAsync(copyText('NEKHRF')).toBeResolvedTo(false);
  });

  it('uses the clipboard API in a secure context', async () => {
    spyOnProperty(window, 'isSecureContext', 'get').and.returnValue(true);
    const writeText = jasmine.createSpy('writeText').and.resolveTo(undefined);
    spyOnProperty(navigator, 'clipboard', 'get').and.returnValue({
      writeText,
    } as unknown as Clipboard);

    await expectAsync(copyText('D9UR4B')).toBeResolvedTo(true);
    expect(writeText).toHaveBeenCalledWith('D9UR4B');
  });
});
